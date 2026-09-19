package io.trino.datafabric.query;

import java.time.Instant;
import java.util.List;

public record QueryExecutionView(
        String queryId,
        String state,
        List<String> columns,
        List<List<Object>> rows,
        boolean truncated,
        String error,
        Instant startedAt,
        Instant finishedAt) {}
