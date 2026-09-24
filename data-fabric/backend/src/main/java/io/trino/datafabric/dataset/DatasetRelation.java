package io.trino.datafabric.dataset;

import java.time.Instant;

/**
 * A directed join edge between two datasets. {@code origin} is DECLARED (operator confirmed) or
 * INFERRED (suggested by naming/value matching and not yet confirmed).
 */
public record DatasetRelation(
        long id,
        String fromDatasetUid,
        String fromField,
        String toDatasetUid,
        String toField,
        String joinType,
        String cardinality,
        String origin,
        String createdBy,
        Instant createdAt)
{
    public static final String DECLARED = "DECLARED";
    public static final String INFERRED = "INFERRED";
}
