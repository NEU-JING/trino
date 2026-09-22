package io.trino.datafabric.metadata;

import io.trino.datafabric.datasource.DataSource;
import io.trino.datafabric.datasource.DataSourceRepository;
import io.trino.datafabric.permission.PermissionService;
import io.trino.datafabric.security.Role;
import io.trino.datafabric.table.RegisteredTable;
import io.trino.datafabric.table.RegisteredTableRepository;
import io.trino.datafabric.table.TableNotFoundException;
import io.trino.datafabric.trino.TrinoGateway;
import io.trino.datafabric.user.User;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves catalog/schema/table/column metadata for guided input, and serves table structure,
 * sample rows and detail. Operators browse everything they can discover; other users are limited
 * to registered tables they are granted on. All filtering happens here so the frontend never has
 * to make security assumptions.
 */
@Service
public class MetadataService
{
    private static final Set<String> SYSTEM_SCHEMAS = Set.of(
            "information_schema", "pg_catalog", "performance_schema", "mysql", "sys");
    private static final int DEFAULT_SAMPLE_ROWS = 20;
    private static final int MAX_SAMPLE_ROWS = 100;

    private final RegisteredTableRepository tableRepository;
    private final DataSourceRepository dataSourceRepository;
    private final PermissionService permissionService;
    private final TrinoGateway trinoGateway;
    private final Duration sampleTimeout;
    private final Duration cacheTtl;
    private final ExecutorService sampleExecutor;
    private final Map<String, CacheEntry> sampleCache = new ConcurrentHashMap<>();

    public MetadataService(
            RegisteredTableRepository tableRepository,
            DataSourceRepository dataSourceRepository,
            PermissionService permissionService,
            TrinoGateway trinoGateway,
            @Value("${data-fabric.metadata.sample-timeout:10s}") Duration sampleTimeout,
            @Value("${data-fabric.metadata.sample-cache-ttl:30s}") Duration cacheTtl)
    {
        this.tableRepository = tableRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.permissionService = permissionService;
        this.trinoGateway = trinoGateway;
        this.sampleTimeout = sampleTimeout;
        this.cacheTtl = cacheTtl;
        this.sampleExecutor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "data-fabric-metadata");
            thread.setDaemon(true);
            return thread;
        });
    }

    public List<SuggestionView> suggest(User user, String type, String parent, String query)
    {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        List<String> names = switch (type.toLowerCase(Locale.ROOT)) {
            case "catalog" -> catalogNames(user);
            case "schema" -> schemaNames(user, requireParent(parent, 1)[0]);
            case "table" -> {
                String[] parts = requireParent(parent, 2);
                yield tableNames(user, parts[0], parts[1]);
            }
            case "column" -> {
                String[] parts = requireParent(parent, 3);
                yield columnNames(user, parts[0], parts[1], parts[2]);
            }
            default -> throw new IllegalArgumentException("Unknown suggestion type: " + type);
        };
        return toSuggestions(names, query);
    }

    public List<ColumnMetadataView> columns(User user, long tableId)
    {
        RegisteredTable table = requireVisibleTable(user, tableId);
        String sql = ("SELECT column_name, data_type, is_nullable, comment FROM %s.information_schema.columns "
                + "WHERE table_schema = '%s' AND table_name = '%s' ORDER BY ordinal_position")
                .formatted(quote(table.catalogName()), escape(table.schemaName()), escape(table.tableName()));
        TrinoGateway.QueryResult result = trinoGateway.execute(sql, null);

        List<ColumnMetadataView> columns = new ArrayList<>();
        for (List<Object> row : result.rows()) {
            String name = stringAt(row, 0);
            String dataType = stringAt(row, 1);
            String nullable = stringAt(row, 2);
            String comment = stringAt(row, 3);
            columns.add(new ColumnMetadataView(name, dataType, nullable.isEmpty() || "YES".equalsIgnoreCase(nullable), comment));
        }
        return columns;
    }

    public SampleRowsView sample(User user, long tableId, Integer limit)
    {
        RegisteredTable table = requireVisibleTable(user, tableId);
        int rows = limit == null ? DEFAULT_SAMPLE_ROWS : Math.max(1, Math.min(limit, MAX_SAMPLE_ROWS));
        String key = "%s.%s.%s#%d".formatted(table.catalogName(), table.schemaName(), table.tableName(), rows);

        SampleRowsView cached = cachedSample(key);
        if (cached != null) {
            return cached;
        }
        String sql = "SELECT * FROM %s LIMIT %d".formatted(qualified(table), rows);
        TrinoGateway.QueryResult result = executeWithTimeout(sql);
        SampleRowsView view = new SampleRowsView(result.columns(), result.rows());
        sampleCache.put(key, new CacheEntry(view, Instant.now()));
        return view;
    }

    public TableDetailView detail(User user, long tableId)
    {
        RegisteredTable table = requireVisibleTable(user, tableId);
        List<String> principals = permissionService.list().stream()
                .filter(permission -> permission.catalog().equals(table.catalogName())
                        && permission.schema().equals(table.schemaName())
                        && permission.table().equals(table.tableName()))
                .map(permission -> "%s（%s）".formatted(permission.principal(), permission.principalType()))
                .toList();
        return new TableDetailView(
                table.id(),
                table.catalogName(),
                table.schemaName(),
                table.tableName(),
                table.description(),
                table.registeredBy(),
                rowCount(table),
                principals);
    }

    private List<String> catalogNames(User user)
    {
        if (user.role() == Role.OPERATOR) {
            return dataSourceRepository.findAll().stream()
                    .filter(DataSource::enabled)
                    .map(DataSource::name)
                    .sorted()
                    .toList();
        }
        return visibleTables(user).stream()
                .map(RegisteredTable::catalogName)
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> schemaNames(User user, String catalog)
    {
        if (user.role() == Role.OPERATOR) {
            requireEnabledDataSource(catalog);
            return firstColumn("SELECT DISTINCT table_schema FROM %s.information_schema.tables ORDER BY 1"
                    .formatted(quote(catalog))).stream()
                    .filter(schema -> !SYSTEM_SCHEMAS.contains(schema.toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return visibleTables(user).stream()
                .filter(table -> table.catalogName().equals(catalog))
                .map(RegisteredTable::schemaName)
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> tableNames(User user, String catalog, String schema)
    {
        if (user.role() == Role.OPERATOR) {
            requireEnabledDataSource(catalog);
            return firstColumn(
                    "SELECT table_name FROM %s.information_schema.tables WHERE table_schema = '%s' ORDER BY 1"
                            .formatted(quote(catalog), escape(schema)));
        }
        return visibleTables(user).stream()
                .filter(table -> table.catalogName().equals(catalog) && table.schemaName().equals(schema))
                .map(RegisteredTable::tableName)
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> columnNames(User user, String catalog, String schema, String table)
    {
        if (user.role() == Role.OPERATOR) {
            requireEnabledDataSource(catalog);
        }
        else {
            boolean visible = visibleTables(user).stream().anyMatch(registered ->
                    registered.catalogName().equals(catalog)
                            && registered.schemaName().equals(schema)
                            && registered.tableName().equals(table));
            if (!visible) {
                return List.of();
            }
        }
        return firstColumn(
                "SELECT column_name FROM %s.information_schema.columns WHERE table_schema = '%s' AND table_name = '%s' ORDER BY ordinal_position"
                        .formatted(quote(catalog), escape(schema), escape(table)));
    }

    private List<RegisteredTable> visibleTables(User user)
    {
        Map<Long, DataSource> dataSources = dataSourceRepository.findAll().stream()
                .collect(Collectors.toMap(DataSource::id, Function.identity()));
        return tableRepository.findAll().stream()
                .filter(table -> {
                    DataSource dataSource = dataSources.get(table.dataSourceId());
                    return dataSource != null && dataSource.enabled();
                })
                .filter(table -> permissionService.canSelect(user, table.catalogName(), table.schemaName(), table.tableName()))
                .toList();
    }

    private RegisteredTable requireVisibleTable(User user, long tableId)
    {
        RegisteredTable table = tableRepository.findById(tableId).orElseThrow(() -> new TableNotFoundException(tableId));
        DataSource dataSource = dataSourceRepository.findById(table.dataSourceId()).orElse(null);
        if (dataSource == null || !dataSource.enabled()
                || !permissionService.canSelect(user, table.catalogName(), table.schemaName(), table.tableName())) {
            // Hide existence from users without access or when the source is disabled.
            throw new TableNotFoundException(tableId);
        }
        return table;
    }

    private DataSource requireEnabledDataSource(String catalog)
    {
        DataSource dataSource = dataSourceRepository.findByName(catalog)
                .orElseThrow(() -> new MetadataQueryException("Unknown catalog: " + catalog));
        if (!dataSource.enabled()) {
            throw new MetadataQueryException("Data source is disabled: " + catalog);
        }
        return dataSource;
    }

    private List<String> firstColumn(String sql)
    {
        TrinoGateway.QueryResult result = trinoGateway.execute(sql, null);
        List<String> values = new ArrayList<>();
        for (List<Object> row : result.rows()) {
            if (!row.isEmpty() && row.get(0) != null) {
                values.add(String.valueOf(row.get(0)));
            }
        }
        return values;
    }

    private Long rowCount(RegisteredTable table)
    {
        try {
            TrinoGateway.QueryResult result = trinoGateway.execute("SELECT COUNT(*) FROM " + qualified(table), null);
            if (!result.rows().isEmpty() && !result.rows().get(0).isEmpty() && result.rows().get(0).get(0) instanceof Number count) {
                return count.longValue();
            }
        }
        catch (RuntimeException ignored) {
            // Row count is best-effort and must not break the detail panel.
        }
        return null;
    }

    private TrinoGateway.QueryResult executeWithTimeout(String sql)
    {
        Future<TrinoGateway.QueryResult> future = sampleExecutor.submit(() -> trinoGateway.execute(sql, null));
        try {
            return future.get(sampleTimeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException e) {
            future.cancel(true);
            throw new MetadataQueryException("Sample data request timed out after " + sampleTimeout);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MetadataQueryException("Sample data request was interrupted");
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw new MetadataQueryException(cause == null ? e.getMessage() : cause.getMessage(), cause);
        }
    }

    private SampleRowsView cachedSample(String key)
    {
        CacheEntry entry = sampleCache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.createdAt().plus(cacheTtl).isBefore(Instant.now())) {
            sampleCache.remove(key);
            return null;
        }
        return entry.view();
    }

    private static List<SuggestionView> toSuggestions(List<String> names, String query)
    {
        String needle = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return names.stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).contains(needle))
                .distinct()
                .map(name -> new SuggestionView(name, name))
                .toList();
    }

    private static String[] requireParent(String parent, int segments)
    {
        String[] parts = parent == null ? new String[0] : parent.split("\\.", -1);
        if (parts.length != segments) {
            throw new IllegalArgumentException("parent must have %d segment(s)".formatted(segments));
        }
        return parts;
    }

    private static String stringAt(List<Object> row, int index)
    {
        if (index >= row.size() || row.get(index) == null) {
            return "";
        }
        return String.valueOf(row.get(index));
    }

    private static String qualified(RegisteredTable table)
    {
        return quote(table.catalogName()) + "." + quote(table.schemaName()) + "." + quote(table.tableName());
    }

    private static String quote(String identifier)
    {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    private static String escape(String value)
    {
        return value.replace("'", "''");
    }

    @PreDestroy
    public void shutdown()
    {
        sampleExecutor.shutdownNow();
    }

    private record CacheEntry(SampleRowsView view, Instant createdAt) {}
}
