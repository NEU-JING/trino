package io.trino.datafabric.table;

import java.time.Instant;

/** API representation of a registered (queryable) table. */
public record RegisteredTableView(
        long id,
        String catalog,
        String schema,
        String table,
        String description,
        String businessType,
        String registeredBy,
        Instant createdAt) {}
