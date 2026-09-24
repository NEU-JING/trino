package io.trino.datafabric.dataset;

import java.time.Instant;

public record DatasetView(
        long id,
        String uid,
        String name,
        String description,
        String domain,
        String owner,
        String kind,
        String status,
        int currentVersion,
        String materializationMode,
        Instant createdAt,
        Instant updatedAt)
{
    public static DatasetView from(Dataset dataset)
    {
        return new DatasetView(
                dataset.id(),
                dataset.uid(),
                dataset.name(),
                dataset.description(),
                dataset.domain(),
                dataset.owner(),
                dataset.kind().name(),
                dataset.status().name(),
                dataset.currentVersion(),
                dataset.materializationMode().name(),
                dataset.createdAt(),
                dataset.updatedAt());
    }
}
