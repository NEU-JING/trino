package io.trino.datafabric.stats;

import io.trino.datafabric.datasource.DataSource;
import io.trino.datafabric.datasource.DataSourceRepository;
import io.trino.datafabric.permission.Permission;
import io.trino.datafabric.permission.PermissionRepository;
import io.trino.datafabric.permission.PermissionService;
import io.trino.datafabric.permission.PrincipalType;
import io.trino.datafabric.query.QueryService;
import io.trino.datafabric.security.Role;
import io.trino.datafabric.table.RegisteredTable;
import io.trino.datafabric.table.RegisteredTableRepository;
import io.trino.datafabric.user.User;
import io.trino.datafabric.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OverviewService
{
    public static final String DATA_SOURCE_TYPE = "DATA_SOURCE";
    public static final String TABLE_TYPE = "TABLE";
    public static final String USER_TYPE = "USER";
    public static final String ROLE_TYPE = "ROLE";
    public static final String DATA_SOURCE_TABLE_EDGE = "DATA_SOURCE_TABLE";
    public static final String GRANT_EDGE = "GRANT";

    private static final int RECENT_QUERY_LIMIT = 10;

    private final DataSourceRepository dataSourceRepository;
    private final RegisteredTableRepository registeredTableRepository;
    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionService permissionService;
    private final QueryService queryService;

    public OverviewService(
            DataSourceRepository dataSourceRepository,
            RegisteredTableRepository registeredTableRepository,
            UserRepository userRepository,
            PermissionRepository permissionRepository,
            PermissionService permissionService,
            QueryService queryService)
    {
        this.dataSourceRepository = dataSourceRepository;
        this.registeredTableRepository = registeredTableRepository;
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.permissionService = permissionService;
        this.queryService = queryService;
    }

    public OverviewStatsView stats(User user)
    {
        if (isOperator(user)) {
            List<DataSource> dataSources = dataSourceRepository.findAll();
            List<DataSourceHealthView> health = dataSources.stream()
                    .map(dataSource -> new DataSourceHealthView(
                            dataSource.name(), dataSource.businessType().displayName(), dataSource.enabled()))
                    .toList();
            return new OverviewStatsView(
                    dataSources.size(),
                    dataSources.stream().filter(DataSource::enabled).count(),
                    registeredTableRepository.findAll().size(),
                    userRepository.count(),
                    queryService.count(),
                    health,
                    queryService.recent(RECENT_QUERY_LIMIT));
        }

        List<DataSource> dataSources = dataSourceRepository.findAll();
        Map<Long, DataSource> sourcesById = indexById(dataSources);
        List<RegisteredTable> visibleTables = visibleTables(user, sourcesById);
        Set<Long> visibleSourceIds = visibleTables.stream()
                .map(RegisteredTable::dataSourceId)
                .collect(Collectors.toSet());
        return new OverviewStatsView(
                visibleSourceIds.size(),
                visibleSourceIds.stream().filter(id -> sourcesById.get(id).enabled()).count(),
                visibleTables.size(),
                0,
                queryService.countOwnedBy(user.username()),
                List.of(),
                queryService.recentOwnedBy(user.username(), RECENT_QUERY_LIMIT));
    }

    public TopologyView topology(User user)
    {
        boolean operator = isOperator(user);
        List<DataSource> dataSources = dataSourceRepository.findAll();
        Map<Long, DataSource> sourcesById = indexById(dataSources);
        List<RegisteredTable> tables = operator
                ? registeredTableRepository.findAll()
                : visibleTables(user, sourcesById);

        List<TopologyNode> nodes = new ArrayList<>();
        List<TopologyEdge> edges = new ArrayList<>();
        Set<String> nodeIds = new HashSet<>();

        Set<Long> includedSourceIds = tables.stream()
                .map(RegisteredTable::dataSourceId)
                .collect(Collectors.toSet());
        Map<Long, String> dataSourceNodeIds = new LinkedHashMap<>();
        for (DataSource dataSource : dataSources) {
            if (!operator && !includedSourceIds.contains(dataSource.id())) {
                continue;
            }
            String nodeId = "ds:" + dataSource.name();
            dataSourceNodeIds.put(dataSource.id(), nodeId);
            if (nodeIds.add(nodeId)) {
                nodes.add(new TopologyNode(nodeId, dataSource.name(), DATA_SOURCE_TYPE, dataSource.enabled(), null));
            }
        }

        Map<String, String> tableNodeIds = new LinkedHashMap<>();
        for (RegisteredTable table : tables) {
            String key = tableKey(table.catalogName(), table.schemaName(), table.tableName());
            String nodeId = "table:" + key;
            tableNodeIds.put(key, nodeId);
            if (nodeIds.add(nodeId)) {
                nodes.add(new TopologyNode(
                        nodeId, table.schemaName() + "." + table.tableName(), TABLE_TYPE, null, null));
            }
            String sourceNodeId = dataSourceNodeIds.get(table.dataSourceId());
            if (sourceNodeId != null) {
                edges.add(new TopologyEdge(sourceNodeId, nodeId, DATA_SOURCE_TABLE_EDGE));
            }
        }

        List<User> users = operator
                ? userRepository.findAll()
                : userRepository.findByUsername(user.username()).stream().toList();
        for (User visibleUser : users) {
            String nodeId = "user:" + visibleUser.username();
            if (nodeIds.add(nodeId)) {
                nodes.add(new TopologyNode(
                        nodeId, visibleUser.username(), USER_TYPE, visibleUser.enabled(), visibleUser.role().name()));
            }
        }

        for (Permission permission : permissionRepository.findAll()) {
            String tableNodeId = tableNodeIds.get(
                    tableKey(permission.catalogName(), permission.schemaName(), permission.tableName()));
            if (tableNodeId == null) {
                continue;
            }
            boolean role = permission.principalType() == PrincipalType.ROLE;
            if (!operator && (role || !permission.principal().equals(user.username()))) {
                continue;
            }
            String nodeId = (role ? "role:" : "user:") + permission.principal();
            if (nodeIds.add(nodeId)) {
                nodes.add(new TopologyNode(
                        nodeId,
                        permission.principal(),
                        role ? ROLE_TYPE : USER_TYPE,
                        null,
                        role ? ROLE_TYPE : null));
            }
            edges.add(new TopologyEdge(tableNodeId, nodeId, GRANT_EDGE));
        }

        return new TopologyView(nodes, edges);
    }

    private List<RegisteredTable> visibleTables(User user, Map<Long, DataSource> sourcesById)
    {
        return registeredTableRepository.findAll().stream()
                .filter(table -> {
                    DataSource source = sourcesById.get(table.dataSourceId());
                    return source != null
                            && source.enabled()
                            && permissionService.canSelect(user, table.catalogName(), table.schemaName(), table.tableName());
                })
                .toList();
    }

    private static boolean isOperator(User user)
    {
        return user.role() == Role.OPERATOR;
    }

    private static Map<Long, DataSource> indexById(List<DataSource> dataSources)
    {
        return dataSources.stream().collect(Collectors.toMap(DataSource::id, Function.identity()));
    }

    private static String tableKey(String catalog, String schema, String table)
    {
        return catalog + "." + schema + "." + table;
    }
}
