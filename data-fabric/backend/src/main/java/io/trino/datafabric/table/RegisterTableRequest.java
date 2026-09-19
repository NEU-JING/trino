package io.trino.datafabric.table;

public record RegisterTableRequest(
        long dataSourceId,
        String schema,
        String table,
        String description) {}
