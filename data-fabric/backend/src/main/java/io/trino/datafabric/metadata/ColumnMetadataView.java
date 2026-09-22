package io.trino.datafabric.metadata;

public record ColumnMetadataView(
        String name,
        String type,
        boolean nullable,
        String comment) {}
