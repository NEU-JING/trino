package io.trino.datafabric.dataset;

public record DatasetFieldView(
        String name,
        String label,
        String dataType,
        String role,
        String aggregation,
        String timeGrain,
        String format,
        String unit,
        boolean nullable)
{
    public static DatasetFieldView from(DatasetField field)
    {
        return new DatasetFieldView(
                field.name(),
                field.label(),
                field.dataType(),
                field.role().name(),
                field.aggregation(),
                field.timeGrain(),
                field.format(),
                field.unit(),
                field.nullable());
    }
}
