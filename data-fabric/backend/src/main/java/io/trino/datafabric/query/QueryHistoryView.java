package io.trino.datafabric.query;

import java.time.Instant;

public record QueryHistoryView(
        String queryId,
        String user,
        String sql,
        String state,
        Instant startedAt,
        Instant finishedAt) {}
