package io.trino.datafabric.datasource;

import java.time.Instant;

/** API representation of a data source. Contains no connector name and no password. */
public record DataSourceView(
        long id,
        String name,
        String businessType,
        String host,
        int port,
        String database,
        String user,
        boolean enabled,
        String createdBy,
        Instant createdAt) {}
