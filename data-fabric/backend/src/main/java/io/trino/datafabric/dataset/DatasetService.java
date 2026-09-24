package io.trino.datafabric.dataset;

import io.trino.datafabric.table.RegisteredTable;
import io.trino.datafabric.table.RegisteredTableRepository;
import io.trino.datafabric.table.TableNotFoundException;
import io.trino.datafabric.trino.TrinoGateway;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Owns the dataset lifecycle: definition, immutable versions, publishing to the fabric catalog,
 * materialization and read access. The same compiled SELECT feeds both the queryable view and the
 * model API, so structure and metadata stay in sync.
 */
@Service
public class DatasetService
{
    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z][a-z0-9_]{0,62}");

    private final DatasetRepository repository;
    private final RegisteredTableRepository tableRepository;
    private final DatasetSqlCompiler compiler;
    private final TrinoGateway trinoGateway;

    public DatasetService(
            DatasetRepository repository,
            RegisteredTableRepository tableRepository,
            DatasetSqlCompiler compiler,
            TrinoGateway trinoGateway)
    {
        this.repository = repository;
        this.tableRepository = tableRepository;
        this.compiler = compiler;
        this.trinoGateway = trinoGateway;
    }

    public DatasetDetailView create(String actor, CreateDatasetRequest request)
    {
        String name = normalizeName(request.name());
        DatasetKind kind = parseKind(request.kind());
        DatasetDefinition definition = toDefinition(kind, request);
        List<DatasetField> fields = toFields(kind, request, definition);

        Optional<Dataset> existing = repository.findByName(name);
        if (existing.isPresent() && existing.get().status() != DatasetStatus.DRAFT) {
            throw new DatasetException("Dataset already exists: " + name);
        }

        long id;
        if (existing.isPresent()) {
            id = existing.get().id();
            repository.updateDefinition(id, repository.writeJson(definition), Instant.now());
        }
        else {
            Instant now = Instant.now();
            id = repository.insert(new Dataset(
                    0,
                    UUID.randomUUID().toString(),
                    name,
                    request.description() == null ? "" : request.description(),
                    request.domain(),
                    actor,
                    kind,
                    DatasetStatus.DRAFT,
                    0,
                    MaterializationMode.VIRTUAL,
                    repository.writeJson(definition),
                    now,
                    now));
        }
        repository.replaceFields(id, fields);
        return detail(id);
    }

    public DatasetDetailView updateDefinition(String uid, CreateDatasetRequest request)
    {
        Dataset dataset = requireByUid(uid);
        DatasetKind kind = parseKind(request.kind() == null ? dataset.kind().name() : request.kind());
        DatasetDefinition definition = toDefinition(kind, request);
        List<DatasetField> fields = toFields(kind, request, definition);
        repository.updateDefinition(dataset.id(), repository.writeJson(definition), Instant.now());
        repository.replaceFields(dataset.id(), fields);
        if (dataset.status() != DatasetStatus.DRAFT) {
            repository.updateStatus(dataset.id(), DatasetStatus.DRAFT, Instant.now());
        }
        return detail(dataset.id());
    }

    public DatasetDetailView publish(String uid, String actor)
    {
        Dataset dataset = requireByUid(uid);
        DatasetDefinition definition = repository.readDefinition(dataset.definitionJson());
        List<DatasetField> fields = repository.fields(dataset.id());
        if (fields.isEmpty()) {
            throw new DatasetException("Dataset has no fields; define it before publishing");
        }
        String viewSql = compileSelect(dataset, fields, definition);

        int version = dataset.currentVersion() + 1;
        applyToFabric(dataset, viewSql);
        repository.insertVersion(new DatasetVersion(
                0,
                dataset.id(),
                version,
                DatasetStatus.PUBLISHED,
                dataset.definitionJson(),
                repository.writeJson(fields),
                Instant.now(),
                Instant.now()));
        repository.updateCurrentVersion(dataset.id(), version, Instant.now());
        repository.updateStatus(dataset.id(), DatasetStatus.PUBLISHED, Instant.now());
        recordMaterialization(dataset, compiler.viewName(dataset.domain(), dataset.name()), MaterializationMode.VIRTUAL);
        return detail(dataset.id());
    }

    public DatasetDetailView deprecate(String uid)
    {
        Dataset dataset = requireByUid(uid);
        repository.updateStatus(dataset.id(), DatasetStatus.DEPRECATED, Instant.now());
        return detail(dataset.id());
    }

    public DatasetDetailView setMaterializationMode(String uid, String mode)
    {
        Dataset dataset = requireByUid(uid);
        MaterializationMode materializationMode = parseMaterialization(mode);
        repository.updateMaterializationMode(dataset.id(), materializationMode, Instant.now());
        return detail(dataset.id());
    }

    public DatasetDetailView refreshMaterialization(String uid)
    {
        Dataset dataset = requireByUid(uid);
        DatasetDefinition definition = repository.readDefinition(dataset.definitionJson());
        List<DatasetField> fields = repository.fields(dataset.id());
        String viewSql = compileSelect(dataset, fields, definition);
        if (dataset.materializationMode() == MaterializationMode.MATERIALIZED) {
            String target = compiler.viewName(dataset.domain(), dataset.name());
            try {
                trinoGateway.execute("CREATE SCHEMA IF NOT EXISTS " + compiler.schemaName(dataset.domain()), null);
                trinoGateway.execute("DROP TABLE IF EXISTS " + target, null);
                trinoGateway.execute("CREATE TABLE " + target + " AS\n" + viewSql, null);
                recordMaterialization(dataset, target, MaterializationMode.MATERIALIZED);
            }
            catch (RuntimeException e) {
                recordMaterialization(dataset, target, MaterializationMode.MATERIALIZED, "FAILED", e.getMessage());
                throw new DatasetException("Materialization failed: " + e.getMessage(), e);
            }
        }
        else {
            applyToFabric(dataset, viewSql);
            recordMaterialization(dataset, compiler.viewName(dataset.domain(), dataset.name()), MaterializationMode.VIRTUAL);
        }
        return detail(dataset.id());
    }

    public String compileViewSql(Dataset dataset)
    {
        DatasetDefinition definition = repository.readDefinition(dataset.definitionJson());
        List<DatasetField> fields = repository.fields(dataset.id());
        return compiler.compileView(dataset, fields, definition, baseQualifiedName(dataset, definition), inputRefs(definition));
    }

    public List<DatasetView> list()
    {
        return repository.findAll().stream().map(DatasetView::from).toList();
    }

    public List<DatasetView> listConsumable()
    {
        return repository.findAll().stream()
                .filter(dataset -> dataset.status() != DatasetStatus.DEPRECATED)
                .map(DatasetView::from)
                .toList();
    }

    public DatasetDetailView detail(long id)
    {
        Dataset dataset = repository.findById(id).orElseThrow(() -> new DatasetNotFoundException(id));
        return toDetail(dataset);
    }

    public DatasetDetailView detailByUid(String uid)
    {
        return toDetail(requireByUid(uid));
    }

    public List<DatasetVersionView> versions(String uid)
    {
        Dataset dataset = requireByUid(uid);
        return repository.versions(dataset.id()).stream().map(DatasetVersionView::from).toList();
    }

    public Dataset requireByUid(String uid)
    {
        return repository.findByUid(uid).orElseThrow(() -> new DatasetNotFoundException(uid));
    }

    public void delete(String uid)
    {
        repository.delete(requireByUid(uid).id());
    }

    public DatasetRepository repository()
    {
        return repository;
    }

    public DatasetSqlCompiler compiler()
    {
        return compiler;
    }

    public static List<DatasetField> diffBreakingChanges(List<DatasetField> previous, List<DatasetField> next)
    {
        Map<String, DatasetField> nextByName = new LinkedHashMap<>();
        next.forEach(field -> nextByName.put(field.name(), field));
        List<DatasetField> removed = new ArrayList<>();
        for (DatasetField before : previous) {
            DatasetField after = nextByName.get(before.name());
            if (after == null) {
                removed.add(before);
            }
            else if (!before.role().equals(after.role())) {
                removed.add(before);
            }
        }
        return removed;
    }

    public DatasetDetailView writeBack(String actor, CreateDatasetRequest request)
    {
        if (request.kind() == null || !DatasetKind.AGGREGATE.name().equalsIgnoreCase(request.kind())) {
            throw new DatasetException("Write-back only supports AGGREGATE datasets");
        }
        String name = normalizeName(request.name());
        Optional<Dataset> existing = repository.findByName(name);
        if (existing.isPresent() && !existing.get().owner().equals(actor)) {
            throw new DatasetException("Dataset namespace is owned by " + existing.get().owner());
        }
        String domain = request.domain() == null || request.domain().isBlank() ? "app" : request.domain();
        CreateDatasetRequest scoped = new CreateDatasetRequest(
                request.name(),
                request.description(),
                domain,
                DatasetKind.AGGREGATE.name(),
                request.baseTableId(),
                request.inputs(),
                request.joins(),
                request.projections(),
                request.filters(),
                request.measures(),
                request.groupBy(),
                request.timeGrain(),
                request.fields());
        if (existing.isPresent()) {
            return updateDefinition(existing.get().uid(), scoped);
        }
        return create(actor, scoped);
    }

    private DatasetDetailView toDetail(Dataset dataset)
    {
        List<DatasetFieldView> fields = repository.fields(dataset.id()).stream().map(DatasetFieldView::from).toList();
        Materialization materialization = repository.materialization(dataset.id()).orElse(null);
        return new DatasetDetailView(
                dataset.id(),
                dataset.uid(),
                dataset.name(),
                dataset.description(),
                dataset.domain(),
                dataset.owner(),
                dataset.kind().name(),
                dataset.status().name(),
                dataset.currentVersion(),
                dataset.materializationMode().name(),
                fields,
                DatasetDetailView.MaterializationView.from(materialization),
                dataset.createdAt(),
                dataset.updatedAt());
    }

    private String compileSelect(Dataset dataset, List<DatasetField> fields, DatasetDefinition definition)
    {
        return compiler.compileSelect(dataset, fields, definition, baseQualifiedName(dataset, definition), inputRefs(definition));
    }

    private void applyToFabric(Dataset dataset, String viewSql)
    {
        String viewName = compiler.viewName(dataset.domain(), dataset.name());
        try {
            trinoGateway.execute("CREATE SCHEMA IF NOT EXISTS " + compiler.schemaName(dataset.domain()), null);
            trinoGateway.execute("CREATE OR REPLACE VIEW " + viewName + " SECURITY INVOKER AS\n" + viewSql, null);
        }
        catch (RuntimeException e) {
            throw new DatasetException("Failed to publish dataset view: " + e.getMessage(), e);
        }
    }

    private void recordMaterialization(Dataset dataset, String target, MaterializationMode mode)
    {
        recordMaterialization(dataset, target, mode, "READY", null);
    }

    private void recordMaterialization(Dataset dataset, String target, MaterializationMode mode, String status, String message)
    {
        Materialization existing = repository.materialization(dataset.id()).orElse(null);
        Instant refreshedAt = "READY".equals(status) ? Instant.now() : (existing == null ? null : existing.refreshedAt());
        repository.upsertMaterialization(new Materialization(
                0,
                dataset.id(),
                mode,
                target,
                status,
                message,
                refreshedAt,
                refreshedAt == null ? null : 0L));
    }

    private String baseQualifiedName(Dataset dataset, DatasetDefinition definition)
    {
        if (dataset.kind() != DatasetKind.BASE) {
            return null;
        }
        if (definition.baseTableId() == null) {
            throw new DatasetException("BASE dataset requires baseTableId");
        }
        RegisteredTable table = tableRepository.findById(definition.baseTableId())
                .orElseThrow(() -> new TableNotFoundException(definition.baseTableId()));
        return DatasetSqlCompiler.quote(table.catalogName())
                + "." + DatasetSqlCompiler.quote(table.schemaName())
                + "." + DatasetSqlCompiler.quote(table.tableName());
    }

    private Map<String, DatasetSqlCompiler.InputRef> inputRefs(DatasetDefinition definition)
    {
        Map<String, DatasetSqlCompiler.InputRef> refs = new LinkedHashMap<>();
        int index = 0;
        for (String uid : definition.inputs()) {
            Dataset input = requireByUid(uid);
            refs.put(uid, new DatasetSqlCompiler.InputRef("t" + index++, compiler.viewName(input.domain(), input.name())));
        }
        return refs;
    }

    private List<DatasetField> toFields(DatasetKind kind, CreateDatasetRequest request, DatasetDefinition definition)
    {
        if (request.fields() != null && !request.fields().isEmpty()) {
            List<DatasetField> fields = new ArrayList<>();
            int ordinal = 0;
            for (CreateDatasetRequest.FieldRequest field : request.fields()) {
                fields.add(toField(0, field, ordinal++));
            }
            return fields;
        }
        if (kind == DatasetKind.BASE) {
            return baseFields(definition.baseTableId());
        }
        if (kind == DatasetKind.AGGREGATE) {
            List<DatasetField> fields = new ArrayList<>();
            int ordinal = 0;
            for (String group : definition.groupBy()) {
                fields.add(new DatasetField(0, 0, deriveName(group, "dim_" + ordinal), group, null,
                        FieldRole.DIMENSION, null, null, null, null, true, ordinal++));
            }
            for (DatasetDefinition.MeasureSpec measure : definition.measures()) {
                fields.add(new DatasetField(0, 0, measure.name(), measure.name(), null,
                        FieldRole.MEASURE, measure.aggregation(), null, null, null, true, ordinal++));
            }
            return fields;
        }
        List<DatasetField> fields = new ArrayList<>();
        int ordinal = 0;
        for (String projection : definition.projections()) {
            fields.add(new DatasetField(0, 0, deriveName(projection, "col_" + ordinal), projection, null,
                    FieldRole.DIMENSION, null, null, null, null, true, ordinal++));
        }
        return fields;
    }

    private List<DatasetField> baseFields(Long baseTableId)
    {
        if (baseTableId == null) {
            throw new DatasetException("BASE dataset requires baseTableId");
        }
        RegisteredTable table = tableRepository.findById(baseTableId)
                .orElseThrow(() -> new TableNotFoundException(baseTableId));
        String sql = ("SELECT column_name, data_type, is_nullable FROM %s.information_schema.columns "
                + "WHERE table_schema = '%s' AND table_name = '%s' ORDER BY ordinal_position")
                .formatted(
                        DatasetSqlCompiler.quote(table.catalogName()),
                        table.schemaName().replace("'", "''"),
                        table.tableName().replace("'", "''"));
        TrinoGateway.QueryResult result = trinoGateway.execute(sql, null);
        List<DatasetField> fields = new ArrayList<>();
        int ordinal = 0;
        for (List<Object> row : result.rows()) {
            String column = String.valueOf(row.get(0));
            String dataType = row.size() > 1 && row.get(1) != null ? String.valueOf(row.get(1)) : null;
            boolean nullable = row.size() < 3 || row.get(2) == null || !"NO".equalsIgnoreCase(String.valueOf(row.get(2)));
            FieldRole role = column.toLowerCase(Locale.ROOT).endsWith("_id") || "id".equalsIgnoreCase(column)
                    ? FieldRole.ID
                    : FieldRole.DIMENSION;
            fields.add(new DatasetField(0, 0, column, column, dataType, role, null, null, null, null, nullable, ordinal++));
        }
        return fields;
    }

    private static DatasetField toField(long datasetId, CreateDatasetRequest.FieldRequest field, int ordinal)
    {
        if (field.name() == null || field.name().isBlank()) {
            throw new IllegalArgumentException("field name is required");
        }
        FieldRole role = field.role() == null || field.role().isBlank()
                ? FieldRole.DIMENSION
                : FieldRole.valueOf(field.role().toUpperCase(Locale.ROOT));
        return new DatasetField(
                0,
                datasetId,
                field.name(),
                field.label() == null ? field.name() : field.label(),
                field.dataType(),
                role,
                field.aggregation(),
                field.timeGrain(),
                field.format(),
                field.unit(),
                field.nullable() == null || field.nullable(),
                ordinal);
    }

    private static DatasetDefinition toDefinition(DatasetKind kind, CreateDatasetRequest request)
    {
        List<DatasetDefinition.JoinSpec> joins = request.joins() == null
                ? List.of()
                : request.joins().stream()
                        .map(join -> new DatasetDefinition.JoinSpec(
                                join.leftDatasetUid(), join.leftField(), join.rightDatasetUid(), join.rightField(),
                                join.joinType(), join.cardinality()))
                        .toList();
        List<DatasetDefinition.MeasureSpec> measures = request.measures() == null
                ? List.of()
                : request.measures().stream()
                        .map(measure -> new DatasetDefinition.MeasureSpec(
                                measure.name(), measure.expression(), measure.aggregation()))
                        .toList();
        if (kind == DatasetKind.BASE && request.baseTableId() == null) {
            throw new IllegalArgumentException("baseTableId is required for BASE datasets");
        }
        if (kind != DatasetKind.BASE && (request.inputs() == null || request.inputs().isEmpty())) {
            throw new IllegalArgumentException("inputs are required for DERIVED/AGGREGATE datasets");
        }
        return new DatasetDefinition(
                request.baseTableId(),
                request.inputs(),
                joins,
                request.projections(),
                request.filters(),
                measures,
                request.groupBy(),
                request.timeGrain());
    }

    private static String deriveName(String expression, String fallback)
    {
        String value = expression.trim();
        int asIndex = value.toLowerCase(Locale.ROOT).lastIndexOf(" as ");
        if (asIndex >= 0) {
            value = value.substring(asIndex + 4).trim();
        }
        else if (value.contains(".")) {
            value = value.substring(value.lastIndexOf('.') + 1);
        }
        value = value.replace("\"", "").replace("`", "").trim();
        if (value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return value.toLowerCase(Locale.ROOT);
        }
        return fallback;
    }

    private static String normalizeName(String name)
    {
        if (name == null) {
            throw new IllegalArgumentException("name is required");
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (!NAME_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("name must match [a-z][a-z0-9_]* and be at most 63 characters");
        }
        return normalized;
    }

    private static DatasetKind parseKind(String kind)
    {
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("kind is required");
        }
        try {
            return DatasetKind.valueOf(kind.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown dataset kind: " + kind);
        }
    }

    private static MaterializationMode parseMaterialization(String mode)
    {
        if (mode == null || mode.isBlank()) {
            throw new IllegalArgumentException("mode is required");
        }
        try {
            return MaterializationMode.valueOf(mode.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown materialization mode: " + mode);
        }
    }
}
