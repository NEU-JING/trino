package io.trino.datafabric.datasource;

/** Connection details stored for a data source. Never returned by the API. */
public record DataSourceConnection(String host, int port, String database, String user, String password) {}
