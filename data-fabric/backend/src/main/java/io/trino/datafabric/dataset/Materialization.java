package io.trino.datafabric.dataset;

import java.time.Instant;

public record Materialization(
        long id,
        long datasetId,
        MaterializationMode mode,
        String target,
        String status,
        String message,
        Instant refreshedAt,
        Long staleSeconds)
{
}
