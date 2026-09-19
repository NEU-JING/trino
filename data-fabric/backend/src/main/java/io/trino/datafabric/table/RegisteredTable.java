package io.trino.datafabric.table;

import java.time.Instant;

public record RegisteredTable(
        long id,
        long dataSourceId,
        String catalogName,
        String schemaName,
        String tableName,
        String description,
        String registeredBy,
        Instant createdAt) {}
