package io.trino.datafabric.dataset;

import java.time.Instant;

/**
 * Immutable snapshot of a dataset definition. Once PUBLISHED a version never changes; breaking
 * changes require publishing a new version so consumers bound to the stable uid are unaffected.
 */
public record DatasetVersion(
        long id,
        long datasetId,
        int version,
        DatasetStatus status,
        String definitionJson,
        String fieldsJson,
        Instant publishedAt,
        Instant createdAt)
{
}
