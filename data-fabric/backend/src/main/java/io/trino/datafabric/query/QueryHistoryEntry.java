package io.trino.datafabric.query;

import java.time.Instant;

public record QueryHistoryEntry(
        long id,
        String queryId,
        String user,
        String sql,
        String state,
        Instant startedAt,
        Instant finishedAt,
        Long rowCount,
        boolean truncated,
        String error) {}
