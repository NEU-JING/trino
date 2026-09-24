package io.trino.datafabric.dataset;

import io.trino.datafabric.table.RegisteredTable;
import io.trino.datafabric.table.RegisteredTableRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dataset relationships (join edges), relation inference, the relation graph and lineage.
 * Relations are shared: a single declared edge can be reused by many datasets.
 */
@Service
public class DatasetRelationService
{
    private static final List<String> ID_SUFFIXES = List.of("_id", "_no", "_code", "_key", "_uuid");

    private final DatasetRelationRepository relationRepository;
    private final DatasetRepository datasetRepository;
    private final RegisteredTableRepository tableRepository;
    private final DatasetUsageRepository usageRepository;

    public DatasetRelationService(
            DatasetRelationRepository relationRepository,
            DatasetRepository datasetRepository,
            RegisteredTableRepository tableRepository,
            DatasetUsageRepository usageRepository)
    {
        this.relationRepository = relationRepository;
        this.datasetRepository = datasetRepository;
        this.tableRepository = tableRepository;
        this.usageRepository = usageRepository;
    }

    public DatasetRelationView declare(String actor, DatasetRelationRequest request)
    {
        requireDataset(request.fromDatasetUid());
        requireDataset(request.toDatasetUid());
        require(request.fromField(), "fromField");
        require(request.toField(), "toField");
        String joinType = request.joinType() == null || request.joinType().isBlank() ? "INNER" : request.joinType().toUpperCase(Locale.ROOT);
        String cardinality = request.cardinality() == null || request.cardinality().isBlank() ? "N:1" : request.cardinality();
        Optional<DatasetRelation> existing = relationRepository.findExisting(
                request.fromDatasetUid(), request.fromField(), request.toDatasetUid(), request.toField(), joinType);
        if (existing.isPresent()) {
            return DatasetRelationView.from(existing.get());
        }
        long id = relationRepository.insert(new DatasetRelation(
                0,
                request.fromDatasetUid(),
                request.fromField(),
                request.toDatasetUid(),
                request.toField(),
                joinType,
                cardinality,
                DatasetRelation.DECLARED,
                actor,
                Instant.now()));
        return DatasetRelationView.from(relationRepository.findById(id).orElseThrow());
    }

    public List<DatasetRelationView> list()
    {
        return relationRepository.findAll().stream().map(DatasetRelationView::from).toList();
    }

    public List<DatasetRelationView> forDataset(String uid)
    {
        return relationRepository.findByDatasetUid(uid).stream().map(DatasetRelationView::from).toList();
    }

    public DatasetRelationView confirm(long id)
    {
        DatasetRelation relation = relationRepository.findById(id).orElseThrow(() -> new DatasetException("Relation not found: " + id));
        relationRepository.confirm(id);
        return DatasetRelationView.from(relationRepository.findById(relation.id()).orElseThrow());
    }

    public void delete(long id)
    {
        relationRepository.delete(id);
    }

    public List<DatasetRelationView> infer(String actor)
    {
        List<Dataset> datasets = datasetRepository.findAll();
        Map<String, List<DatasetField>> fieldsByUid = datasets.stream()
                .collect(Collectors.toMap(Dataset::uid, dataset -> datasetRepository.fields(dataset.id())));
        List<DatasetRelationView> created = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < datasets.size(); i++) {
            for (int j = i + 1; j < datasets.size(); j++) {
                Dataset left = datasets.get(i);
                Dataset right = datasets.get(j);
                for (DatasetField leftField : fieldsByUid.getOrDefault(left.uid(), List.of())) {
                    for (DatasetField rightField : fieldsByUid.getOrDefault(right.uid(), List.of())) {
                        if (!compatible(leftField.name(), rightField.name())) {
                            continue;
                        }
                        String key = left.uid() + "|" + leftField.name() + "|" + right.uid() + "|" + rightField.name();
                        if (!seen.add(key)) {
                            continue;
                        }
                        if (relationRepository.findExisting(left.uid(), leftField.name(), right.uid(), rightField.name(), "INNER").isPresent()
                                || relationRepository.findExisting(right.uid(), rightField.name(), left.uid(), leftField.name(), "INNER").isPresent()) {
                            continue;
                        }
                        long id = relationRepository.insert(new DatasetRelation(
                                0,
                                left.uid(),
                                leftField.name(),
                                right.uid(),
                                rightField.name(),
                                "INNER",
                                "N:1",
                                DatasetRelation.INFERRED,
                                actor,
                                Instant.now()));
                        created.add(DatasetRelationView.from(relationRepository.findById(id).orElseThrow()));
                    }
                }
            }
        }
        return created;
    }

    public RelationGraphView graph()
    {
        List<RelationGraphView.Node> nodes = datasetRepository.findAll().stream()
                .map(dataset -> new RelationGraphView.Node(
                        dataset.uid(), dataset.name(), dataset.kind().name(), dataset.status().name()))
                .toList();
        return new RelationGraphView(nodes, list());
    }

    public LineageView lineage(String uid)
    {
        Dataset dataset = requireDataset(uid);
        DatasetDefinition definition = datasetRepository.readDefinition(dataset.definitionJson());
        List<LineageView.LineageNode> upstream = new ArrayList<>();
        for (String inputUid : definition.inputs()) {
            datasetRepository.findByUid(inputUid).ifPresent(input ->
                    upstream.add(new LineageView.LineageNode(input.uid(), input.name(), "dataset")));
        }
        if (dataset.kind() == DatasetKind.BASE && definition.baseTableId() != null) {
            tableRepository.findById(definition.baseTableId()).ifPresent(table ->
                    upstream.add(new LineageView.LineageNode(
                            "table:" + table.id(), qualified(table), "table")));
        }
        List<LineageView.LineageNode> downstream = new ArrayList<>();
        for (Dataset candidate : datasetRepository.findAll()) {
            if (candidate.uid().equals(uid)) {
                continue;
            }
            DatasetDefinition candidateDefinition = datasetRepository.readDefinition(candidate.definitionJson());
            if (candidateDefinition.inputs().contains(uid)) {
                downstream.add(new LineageView.LineageNode(candidate.uid(), candidate.name(), "dataset"));
            }
        }
        usageRepository.findByDatasetUid(uid).stream()
                .map(DatasetUsage::application)
                .distinct()
                .forEach(application -> downstream.add(new LineageView.LineageNode("app:" + application, application, "application")));
        return new LineageView(uid, upstream, downstream);
    }

    private static boolean compatible(String left, String right)
    {
        if (!left.equalsIgnoreCase(right)) {
            return false;
        }
        return isIdLike(left);
    }

    private static boolean isIdLike(String name)
    {
        String lower = name.toLowerCase(Locale.ROOT);
        if ("id".equals(lower)) {
            return true;
        }
        return ID_SUFFIXES.stream().anyMatch(lower::endsWith);
    }

    private Dataset requireDataset(String uid)
    {
        if (uid == null || uid.isBlank()) {
            throw new IllegalArgumentException("dataset uid is required");
        }
        return datasetRepository.findByUid(uid).orElseThrow(() -> new DatasetNotFoundException(uid));
    }

    private static void require(String value, String name)
    {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static String qualified(RegisteredTable table)
    {
        return table.catalogName() + "." + table.schemaName() + "." + table.tableName();
    }
}
