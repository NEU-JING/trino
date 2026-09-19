package io.trino.datafabric.table;

import io.trino.datafabric.datasource.DataSource;
import io.trino.datafabric.datasource.DataSourceNotFoundException;
import io.trino.datafabric.datasource.DataSourceRepository;
import io.trino.datafabric.permission.PermissionService;
import io.trino.datafabric.trino.TrinoGateway;
import io.trino.datafabric.user.User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TableCatalogService
{
    private static final Set<String> SYSTEM_SCHEMAS = Set.of(
            "information_schema", "pg_catalog", "performance_schema", "mysql", "sys");

    private final RegisteredTableRepository tableRepository;
    private final DataSourceRepository dataSourceRepository;
    private final TrinoGateway trinoGateway;
    private final PermissionService permissionService;

    public TableCatalogService(
            RegisteredTableRepository tableRepository,
            DataSourceRepository dataSourceRepository,
            TrinoGateway trinoGateway,
            PermissionService permissionService)
    {
        this.tableRepository = tableRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.trinoGateway = trinoGateway;
        this.permissionService = permissionService;
    }

    public List<DiscoveredTable> discover(long dataSourceId)
    {
        DataSource dataSource = requireDataSource(dataSourceId);
        String sql = "SELECT table_schema, table_name, table_type FROM %s.information_schema.tables ORDER BY 1, 2"
                .formatted(dataSource.name());
        TrinoGateway.QueryResult result = trinoGateway.execute(sql, null);

        List<DiscoveredTable> tables = new ArrayList<>();
        for (List<Object> row : result.rows()) {
            String schema = String.valueOf(row.get(0));
            if (SYSTEM_SCHEMAS.contains(schema.toLowerCase(Locale.ROOT))) {
                continue;
            }
            tables.add(new DiscoveredTable(
                    schema,
                    String.valueOf(row.get(1)),
                    row.size() > 2 && row.get(2) != null ? String.valueOf(row.get(2)) : ""));
        }
        return tables;
    }

    public RegisteredTableView register(String actor, RegisterTableRequest request)
    {
        DataSource dataSource = requireDataSource(request.dataSourceId());
        if (request.schema() == null || request.schema().isBlank()) {
            throw new IllegalArgumentException("schema is required");
        }
        if (request.table() == null || request.table().isBlank()) {
            throw new IllegalArgumentException("table is required");
        }
        String description = request.description() == null ? "" : request.description();

        Optional<RegisteredTable> existing = tableRepository.findByCatalogSchemaTable(
                dataSource.name(), request.schema(), request.table());
        long id;
        if (existing.isPresent()) {
            tableRepository.updateDescription(existing.get().id(), description);
            id = existing.get().id();
        }
        else {
            id = tableRepository.insert(new RegisteredTable(
                    0, dataSource.id(), dataSource.name(), request.schema(), request.table(), description, actor, Instant.now()));
        }
        return toView(tableRepository.findById(id).orElseThrow(), dataSource);
    }

    public void unregister(long id)
    {
        tableRepository.findById(id).orElseThrow(() -> new TableNotFoundException(id));
        tableRepository.delete(id);
    }

    public List<RegisteredTableView> listVisible(User user, String query)
    {
        Map<Long, DataSource> dataSources = dataSourceRepository.findAll().stream()
                .collect(Collectors.toMap(DataSource::id, Function.identity()));
        return tableRepository.findAll().stream()
                .filter(table -> {
                    DataSource dataSource = dataSources.get(table.dataSourceId());
                    return dataSource != null && dataSource.enabled();
                })
                .filter(table -> permissionService.canSelect(user, table.catalogName(), table.schemaName(), table.tableName()))
                .filter(table -> matches(table, query))
                .map(table -> toView(table, dataSources.get(table.dataSourceId())))
                .toList();
    }

    private static boolean matches(RegisteredTable table, String query)
    {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        return contains(table.tableName(), needle)
                || contains(table.schemaName(), needle)
                || contains(table.description(), needle);
    }

    private static boolean contains(String value, String needle)
    {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private DataSource requireDataSource(long id)
    {
        return dataSourceRepository.findById(id).orElseThrow(() -> new DataSourceNotFoundException(id));
    }

    private static RegisteredTableView toView(RegisteredTable table, DataSource dataSource)
    {
        return new RegisteredTableView(
                table.id(),
                table.catalogName(),
                table.schemaName(),
                table.tableName(),
                table.description(),
                dataSource.businessType().displayName(),
                table.registeredBy(),
                table.createdAt());
    }
}
