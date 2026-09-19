package io.trino.datafabric.datasource;

import java.time.Instant;

public record DataSource(
        long id,
        String name,
        BusinessType businessType,
        String connectorName,
        DataSourceConnection connection,
        boolean enabled,
        String createdBy,
        Instant createdAt) {}
