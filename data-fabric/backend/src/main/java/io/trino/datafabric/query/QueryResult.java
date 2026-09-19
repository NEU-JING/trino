package io.trino.datafabric.query;

import java.util.List;

public record QueryResult(List<String> columns, List<List<Object>> rows, boolean truncated) {}
