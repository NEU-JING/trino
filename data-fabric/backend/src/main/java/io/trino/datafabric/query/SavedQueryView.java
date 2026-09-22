package io.trino.datafabric.query;

import java.time.Instant;

public record SavedQueryView(
        long id,
        String name,
        String sql,
        Instant createdAt,
        Instant updatedAt) {}
