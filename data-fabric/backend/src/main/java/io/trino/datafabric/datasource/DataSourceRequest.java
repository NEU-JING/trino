package io.trino.datafabric.datasource;

/** API request for creating or updating a data source. */
public record DataSourceRequest(
        String name,
        String businessType,
        String host,
        Integer port,
        String database,
        String user,
        String password) {}
