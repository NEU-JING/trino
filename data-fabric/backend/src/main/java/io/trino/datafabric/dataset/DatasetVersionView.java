package io.trino.datafabric.dataset;

import java.time.Instant;

public record DatasetVersionView(
        int version,
        String status,
        Instant publishedAt,
        Instant createdAt)
{
    public static DatasetVersionView from(DatasetVersion version)
    {
        return new DatasetVersionView(version.version(), version.status().name(), version.publishedAt(), version.createdAt());
    }
}
