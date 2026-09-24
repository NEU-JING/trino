package io.trino.datafabric.dataset;

import java.time.Instant;

public record DatasetUsage(
        long id,
        String datasetUid,
        String application,
        String username,
        long queryCount,
        long failureCount,
        long totalLatencyMs,
        Instant lastUsedAt)
{
    public double averageLatencyMs()
    {
        return queryCount == 0 ? 0 : (double) totalLatencyMs / queryCount;
    }
}
