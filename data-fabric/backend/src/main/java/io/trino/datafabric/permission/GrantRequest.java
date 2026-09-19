package io.trino.datafabric.permission;

public record GrantRequest(
        String principal,
        String principalType,
        String catalog,
        String schema,
        String table,
        String permission) {}
