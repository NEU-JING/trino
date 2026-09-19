package io.trino.datafabric.permission;

import java.time.Instant;

public record Permission(
        String principal,
        PrincipalType principalType,
        String catalogName,
        String schemaName,
        String tableName,
        String permission,
        Instant createdAt) {}
