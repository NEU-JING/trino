package io.trino.datafabric.permission;

public record PermissionView(
        String principal,
        String principalType,
        String catalog,
        String schema,
        String table,
        String permission) {}
