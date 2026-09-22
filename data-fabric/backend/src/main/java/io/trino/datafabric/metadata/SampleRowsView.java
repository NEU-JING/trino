package io.trino.datafabric.metadata;

import java.util.List;

public record SampleRowsView(
        List<String> columns,
        List<List<Object>> rows) {}
