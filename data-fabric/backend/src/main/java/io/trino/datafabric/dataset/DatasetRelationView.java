package io.trino.datafabric.dataset;

public record DatasetRelationView(
        long id,
        String fromDatasetUid,
        String fromField,
        String toDatasetUid,
        String toField,
        String joinType,
        String cardinality,
        String origin)
{
    public static DatasetRelationView from(DatasetRelation relation)
    {
        return new DatasetRelationView(
                relation.id(),
                relation.fromDatasetUid(),
                relation.fromField(),
                relation.toDatasetUid(),
                relation.toField(),
                relation.joinType(),
                relation.cardinality(),
                relation.origin());
    }
}
