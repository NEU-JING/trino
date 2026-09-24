package io.trino.datafabric.dataset;

import java.time.Instant;

/**
 * A logical dataset: the only unit exposed to consumers. {@code uid} is a stable identifier that
 * does not change across versions; {@code name} is the human/business name and the SQL view name.
 */
public record Dataset(
        long id,
        String uid,
        String name,
        String description,
        String domain,
        String owner,
        DatasetKind kind,
        DatasetStatus status,
        int currentVersion,
        MaterializationMode materializationMode,
        String definitionJson,
        Instant createdAt,
        Instant updatedAt)
{
}
