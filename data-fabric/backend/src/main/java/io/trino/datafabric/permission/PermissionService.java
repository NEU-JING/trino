package io.trino.datafabric.permission;

import io.trino.datafabric.security.Role;
import io.trino.datafabric.user.User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Table-level access decisions for the platform layer. Operators can see everything;
 * other users need an explicit grant (directly or via their platform role).
 */
@Service
public class PermissionService
{
    private final PermissionRepository repository;

    public PermissionService(PermissionRepository repository)
    {
        this.repository = repository;
    }

    public boolean canSelect(User user, String catalog, String schema, String table)
    {
        if (user.role() == Role.OPERATOR) {
            return true;
        }
        return repository.isGranted(user.username(), PrincipalType.USER, catalog, schema, table, PermissionRepository.SELECT)
                || repository.isGranted(user.role().name(), PrincipalType.ROLE, catalog, schema, table, PermissionRepository.SELECT);
    }

    public void grant(GrantRequest request)
    {
        String principal = require(request.principal(), "principal");
        PrincipalType principalType = request.principalType() == null
                ? PrincipalType.USER
                : PrincipalType.valueOf(request.principalType().toUpperCase(Locale.ROOT));
        String permission = request.permission() == null || request.permission().isBlank()
                ? PermissionRepository.SELECT
                : request.permission().toUpperCase(Locale.ROOT);
        repository.grant(
                principal,
                principalType,
                require(request.catalog(), "catalog"),
                require(request.schema(), "schema"),
                require(request.table(), "table"),
                permission);
    }

    public void revoke(GrantRequest request)
    {
        String principal = require(request.principal(), "principal");
        PrincipalType principalType = request.principalType() == null
                ? PrincipalType.USER
                : PrincipalType.valueOf(request.principalType().toUpperCase(Locale.ROOT));
        String permission = request.permission() == null || request.permission().isBlank()
                ? PermissionRepository.SELECT
                : request.permission().toUpperCase(Locale.ROOT);
        repository.revoke(
                principal,
                principalType,
                require(request.catalog(), "catalog"),
                require(request.schema(), "schema"),
                require(request.table(), "table"),
                permission);
    }

    public List<PermissionView> list()
    {
        return repository.findAll().stream()
                .map(permission -> new PermissionView(
                        permission.principal(),
                        permission.principalType().name(),
                        permission.catalogName(),
                        permission.schemaName(),
                        permission.tableName(),
                        permission.permission()))
                .toList();
    }

    private static String require(String value, String name)
    {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
