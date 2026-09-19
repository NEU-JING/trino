package io.trino.datafabric.trino;

import java.util.List;
import java.util.Map;

/**
 * Thin abstraction over the Trino coordinator used by the platform.
 * Implemented by {@link HttpTrinoGateway}; faked in tests.
 */
public interface TrinoGateway
{
    QueryResult execute(String sql, String actingUser);

    void createCatalog(String catalog, String connector, Map<String, String> properties);

    void dropCatalog(String catalog);

    /** Runs a lightweight query against the catalog; throws if it is unreachable. */
    void probeCatalog(String catalog);

    record QueryResult(List<String> columns, List<List<Object>> rows) {}
}
