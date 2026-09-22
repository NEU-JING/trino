package io.trino.datafabric.query;

import java.time.Instant;

public record SavedQuery(
        long id,
        String name,
        String sql,
        String owner,
        Instant createdAt,
        Instant updatedAt) {}
