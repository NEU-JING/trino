package io.trino.datafabric.dataset;

import java.util.List;

public record CreateDatasetRequest(
        String name,
        String description,
        String domain,
        String kind,
        Long baseTableId,
        List<String> inputs,
        List<JoinRequest> joins,
        List<String> projections,
        List<String> filters,
        List<MeasureRequest> measures,
        List<String> groupBy,
        String timeGrain,
        List<FieldRequest> fields)
{
    public record JoinRequest(
            String leftDatasetUid,
            String leftField,
            String rightDatasetUid,
            String rightField,
            String joinType,
            String cardinality)
    {
    }

    public record MeasureRequest(String name, String expression, String aggregation)
    {
    }

    public record FieldRequest(
            String name,
            String label,
            String dataType,
            String role,
            String aggregation,
            String timeGrain,
            String format,
            String unit,
            Boolean nullable)
    {
    }
}
