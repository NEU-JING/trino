package io.trino.datafabric.support;

import io.trino.datafabric.trino.TrinoGateway;
import io.trino.datafabric.trino.TrinoQueryException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hand-written in-memory fake (no mocking library). Simulates a Trino coordinator:
 * tracks catalogs and fails probes when the connection URL points at "unreachable".
 */
public class FakeTrinoGateway
        implements TrinoGateway
{
    private final Map<String, String> catalogs = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> catalogProperties = new ConcurrentHashMap<>();
    private final Map<String, QueryResult> queryResults = new ConcurrentHashMap<>();
    private List<String> queryColumns = List.of();
    private List<List<Object>> queryRows = List.of();

    public void setQueryResult(List<String> columns, List<List<Object>> rows)
    {
        this.queryColumns = List.copyOf(columns);
        this.queryRows = List.copyOf(rows);
    }

    /** Returns the mapped result for any SQL containing {@code sqlFragment}, before the default. */
    public void setQueryResult(String sqlFragment, List<String> columns, List<List<Object>> rows)
    {
        queryResults.put(sqlFragment, new QueryResult(List.copyOf(columns), List.copyOf(rows)));
    }

    @Override
    public QueryResult execute(String sql, String actingUser)
    {
        for (Map.Entry<String, QueryResult> entry : queryResults.entrySet()) {
            if (sql.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return new QueryResult(queryColumns, queryRows);
    }

    @Override
    public void createCatalog(String catalog, String connector, Map<String, String> properties)
    {
        catalogs.put(catalog, connector);
        catalogProperties.put(catalog, Map.copyOf(properties));
    }

    @Override
    public void dropCatalog(String catalog)
    {
        catalogs.remove(catalog);
        catalogProperties.remove(catalog);
    }

    @Override
    public void probeCatalog(String catalog)
    {
        if (!catalogs.containsKey(catalog)) {
            throw new TrinoQueryException("Catalog not found: " + catalog);
        }
        String url = catalogProperties.getOrDefault(catalog, Map.of()).getOrDefault("connection-url", "");
        if (url.contains("unreachable")) {
            throw new TrinoQueryException("Connection refused");
        }
    }

    public boolean hasCatalog(String catalog)
    {
        return catalogs.containsKey(catalog);
    }

    public String catalogConnector(String catalog)
    {
        return catalogs.get(catalog);
    }

    public String connectionUrl(String catalog)
    {
        return catalogProperties.getOrDefault(catalog, Map.of()).get("connection-url");
    }

    public Set<String> catalogNames()
    {
        return Set.copyOf(catalogs.keySet());
    }

    public void reset()
    {
        catalogs.clear();
        catalogProperties.clear();
        queryResults.clear();
        queryColumns = List.of();
        queryRows = List.of();
    }
}
