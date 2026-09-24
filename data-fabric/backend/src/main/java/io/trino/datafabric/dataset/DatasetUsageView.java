package io.trino.datafabric.dataset;

import java.time.Instant;

public record DatasetUsageView(
        String datasetUid,
        String application,
        String username,
        long queryCount,
        long failureCount,
        double averageLatencyMs,
        Instant lastUsedAt)
{
    public static DatasetUsageView from(DatasetUsage usage)
    {
        return new DatasetUsageView(
                usage.datasetUid(),
                usage.application(),
                usage.username(),
                usage.queryCount(),
                usage.failureCount(),
                usage.averageLatencyMs(),
                usage.lastUsedAt());
    }
}
