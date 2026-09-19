package io.trino.datafabric.table;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class RegisteredTableRepository
{
    private static final String SELECT = "SELECT id, data_source_id, catalog_name, schema_name, table_name, description, registered_by, created_at FROM registered_table";

    private final JdbcTemplate jdbcTemplate;

    public RegisteredTableRepository(JdbcTemplate jdbcTemplate)
    {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<RegisteredTable> findById(long id)
    {
        return jdbcTemplate.query(SELECT + " WHERE id = ?", this::mapRow, id).stream().findFirst();
    }

    public Optional<RegisteredTable> findByCatalogSchemaTable(String catalog, String schema, String table)
    {
        return jdbcTemplate.query(
                SELECT + " WHERE catalog_name = ? AND schema_name = ? AND table_name = ?",
                this::mapRow, catalog, schema, table).stream().findFirst();
    }

    public List<RegisteredTable> findAll()
    {
        return jdbcTemplate.query(SELECT + " ORDER BY catalog_name, schema_name, table_name", this::mapRow);
    }

    public long insert(RegisteredTable table)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO registered_table (data_source_id, catalog_name, schema_name, table_name, description, registered_by, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, table.dataSourceId());
            statement.setString(2, table.catalogName());
            statement.setString(3, table.schemaName());
            statement.setString(4, table.tableName());
            statement.setString(5, table.description());
            statement.setString(6, table.registeredBy());
            statement.setTimestamp(7, Timestamp.from(table.createdAt()));
            return statement;
        }, keyHolder);

        Map<String, Object> keys = keyHolder.getKeys();
        if (keys == null || keys.isEmpty()) {
            throw new IllegalStateException("No generated key for registered table " + table.tableName());
        }
        return ((Number) keys.values().iterator().next()).longValue();
    }

    public void updateDescription(long id, String description)
    {
        jdbcTemplate.update("UPDATE registered_table SET description = ? WHERE id = ?", description, id);
    }

    public void delete(long id)
    {
        jdbcTemplate.update("DELETE FROM registered_table WHERE id = ?", id);
    }

    private RegisteredTable mapRow(java.sql.ResultSet resultSet, int rowNum)
            throws java.sql.SQLException
    {
        return new RegisteredTable(
                resultSet.getLong("id"),
                resultSet.getLong("data_source_id"),
                resultSet.getString("catalog_name"),
                resultSet.getString("schema_name"),
                resultSet.getString("table_name"),
                resultSet.getString("description"),
                resultSet.getString("registered_by"),
                resultSet.getTimestamp("created_at").toInstant());
    }
}
