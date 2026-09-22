package io.trino.datafabric.metadata;

import java.util.List;

public record TableDetailView(
        long id,
        String catalog,
        String schema,
        String table,
        String description,
        String registeredBy,
        Long rowCount,
        List<String> principals) {}
