package io.trino.datafabric.permission;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class PermissionRepository
{
    public static final String SELECT = "SELECT";

    private final JdbcTemplate jdbcTemplate;

    public PermissionRepository(JdbcTemplate jdbcTemplate)
    {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isGranted(String principal, PrincipalType principalType, String catalog, String schema, String table, String permission)
    {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM access_permission WHERE principal = ? AND principal_type = ? AND catalog_name = ? "
                        + "AND schema_name = ? AND table_name = ? AND permission = ?",
                Integer.class, principal, principalType.name(), catalog, schema, table, permission);
        return count != null && count > 0;
    }

    public void grant(String principal, PrincipalType principalType, String catalog, String schema, String table, String permission)
    {
        if (isGranted(principal, principalType, catalog, schema, table, permission)) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO access_permission (principal, principal_type, catalog_name, schema_name, table_name, permission, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                principal, principalType.name(), catalog, schema, table, permission, Timestamp.from(Instant.now()));
    }

    public void revoke(String principal, PrincipalType principalType, String catalog, String schema, String table, String permission)
    {
        jdbcTemplate.update(
                "DELETE FROM access_permission WHERE principal = ? AND principal_type = ? AND catalog_name = ? "
                        + "AND schema_name = ? AND table_name = ? AND permission = ?",
                principal, principalType.name(), catalog, schema, table, permission);
    }

    public List<Permission> findAll()
    {
        return jdbcTemplate.query(
                "SELECT principal, principal_type, catalog_name, schema_name, table_name, permission, created_at FROM access_permission",
                (rs, rowNum) -> new Permission(
                        rs.getString("principal"),
                        PrincipalType.valueOf(rs.getString("principal_type")),
                        rs.getString("catalog_name"),
                        rs.getString("schema_name"),
                        rs.getString("table_name"),
                        rs.getString("permission"),
                        rs.getTimestamp("created_at").toInstant()));
    }
}
