package io.trino.datafabric.dataset;

import java.util.List;

/**
 * Structural definition of a dataset, stored as JSON. BASE references a registered table;
 * DERIVED combines input datasets through joins, projections and filters; AGGREGATE groups
 * input datasets into measures and dimensions.
 */
public record DatasetDefinition(
        Long baseTableId,
        List<String> inputs,
        List<JoinSpec> joins,
        List<String> projections,
        List<String> filters,
        List<MeasureSpec> measures,
        List<String> groupBy,
        String timeGrain)
{
    public DatasetDefinition
    {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        joins = joins == null ? List.of() : List.copyOf(joins);
        projections = projections == null ? List.of() : List.copyOf(projections);
        filters = filters == null ? List.of() : List.copyOf(filters);
        measures = measures == null ? List.of() : List.copyOf(measures);
        groupBy = groupBy == null ? List.of() : List.copyOf(groupBy);
    }

    public static DatasetDefinition empty()
    {
        return new DatasetDefinition(null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null);
    }

    public record JoinSpec(
            String leftDatasetUid,
            String leftField,
            String rightDatasetUid,
            String rightField,
            String joinType,
            String cardinality)
    {
    }

    public record MeasureSpec(String name, String expression, String aggregation)
    {
    }
}
