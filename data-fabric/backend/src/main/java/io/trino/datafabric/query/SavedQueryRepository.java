package io.trino.datafabric.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class SavedQueryRepository
{
    private static final String SELECT = "SELECT id, name, sql_text, owner, created_at, updated_at FROM saved_query";

    private final JdbcTemplate jdbcTemplate;

    public SavedQueryRepository(JdbcTemplate jdbcTemplate)
    {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SavedQuery> findByOwner(String owner)
    {
        return jdbcTemplate.query(
                SELECT + " WHERE owner = ? ORDER BY updated_at DESC, id DESC",
                this::mapRow,
                owner);
    }

    public Optional<SavedQuery> findByOwnerAndName(String owner, String name)
    {
        return jdbcTemplate.query(
                SELECT + " WHERE owner = ? AND name = ?",
                this::mapRow,
                owner,
                name).stream().findFirst();
    }

    public long insert(String name, String sql, String owner, Instant now)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO saved_query (name, sql_text, owner, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, name);
            statement.setString(2, sql);
            statement.setString(3, owner);
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setTimestamp(5, Timestamp.from(now));
            return statement;
        }, keyHolder);

        Map<String, Object> keys = keyHolder.getKeys();
        if (keys == null || keys.isEmpty()) {
            throw new IllegalStateException("No generated key for saved query " + name);
        }
        return ((Number) keys.values().iterator().next()).longValue();
    }

    public void update(long id, String sql, Instant updatedAt)
    {
        jdbcTemplate.update(
                "UPDATE saved_query SET sql_text = ?, updated_at = ? WHERE id = ?",
                sql, Timestamp.from(updatedAt), id);
    }

    public int delete(String owner, long id)
    {
        return jdbcTemplate.update("DELETE FROM saved_query WHERE owner = ? AND id = ?", owner, id);
    }

    private SavedQuery mapRow(ResultSet resultSet, int rowNum)
            throws SQLException
    {
        return new SavedQuery(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getString("sql_text"),
                resultSet.getString("owner"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }
}
