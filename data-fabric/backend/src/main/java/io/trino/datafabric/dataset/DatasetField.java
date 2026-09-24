package io.trino.datafabric.dataset;

public record DatasetField(
        long id,
        long datasetId,
        String name,
        String label,
        String dataType,
        FieldRole role,
        String aggregation,
        String timeGrain,
        String format,
        String unit,
        boolean nullable,
        int ordinal)
{
}
