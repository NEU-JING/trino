package io.trino.datafabric.dataset;

public record DatasetRelationRequest(
        String fromDatasetUid,
        String fromField,
        String toDatasetUid,
        String toField,
        String joinType,
        String cardinality)
{
}
