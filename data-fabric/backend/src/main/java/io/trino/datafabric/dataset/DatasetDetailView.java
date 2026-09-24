package io.trino.datafabric.dataset;

import java.time.Instant;
import java.util.List;

public record DatasetDetailView(
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
        List<DatasetFieldView> fields,
        MaterializationView materialization,
        Instant createdAt,
        Instant updatedAt)
{
    public record MaterializationView(
            String mode,
            String target,
            String status,
            String message,
            Instant refreshedAt,
            Long staleSeconds)
    {
        public static MaterializationView from(Materialization materialization)
        {
            if (materialization == null) {
                return null;
            }
            return new MaterializationView(
                    materialization.mode().name(),
                    materialization.target(),
                    materialization.status(),
                    materialization.message(),
                    materialization.refreshedAt(),
                    materialization.staleSeconds());
        }
    }
}
