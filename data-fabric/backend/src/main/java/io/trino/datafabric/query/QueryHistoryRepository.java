package io.trino.datafabric.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class QueryHistoryRepository
{
    private static final String SELECT = "SELECT id, query_id, username, sql_text, state, started_at, finished_at, row_count, truncated, error_message FROM query_history";

    private final JdbcTemplate jdbcTemplate;

    public QueryHistoryRepository(JdbcTemplate jdbcTemplate)
    {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void started(String queryId, String user, String sql, Instant startedAt)
    {
        jdbcTemplate.update(
                "INSERT INTO query_history (query_id, username, sql_text, state, started_at, finished_at, row_count, truncated, error_message) "
                        + "VALUES (?, ?, ?, ?, ?, NULL, NULL, FALSE, NULL)",
                queryId, user, sql, QueryState.RUNNING.name(), Timestamp.from(startedAt));
    }

    public void completed(String queryId, QueryState state, Instant finishedAt, Long rowCount, boolean truncated, String error)
    {
        jdbcTemplate.update(
                "UPDATE query_history SET state = ?, finished_at = ?, row_count = ?, truncated = ?, error_message = ? WHERE query_id = ?",
                state.name(),
                finishedAt == null ? null : Timestamp.from(finishedAt),
                rowCount,
                truncated,
                error,
                queryId);
    }

    public List<QueryHistoryEntry> findByUser(String user, int limit)
    {
        return jdbcTemplate.query(
                SELECT + " WHERE username = ? ORDER BY started_at DESC, id DESC LIMIT ?",
                this::mapRow,
                user,
                limit);
    }

    public void trim(String user, int keep)
    {
        jdbcTemplate.update(
                "DELETE FROM query_history WHERE username = ? AND id NOT IN "
                        + "(SELECT id FROM query_history WHERE username = ? ORDER BY id DESC LIMIT ?)",
                user, user, keep);
    }

    private QueryHistoryEntry mapRow(ResultSet resultSet, int rowNum)
            throws SQLException
    {
        Timestamp finishedAt = resultSet.getTimestamp("finished_at");
        Number rowCount = (Number) resultSet.getObject("row_count");
        return new QueryHistoryEntry(
                resultSet.getLong("id"),
                resultSet.getString("query_id"),
                resultSet.getString("username"),
                resultSet.getString("sql_text"),
                resultSet.getString("state"),
                resultSet.getTimestamp("started_at").toInstant(),
                finishedAt == null ? null : finishedAt.toInstant(),
                rowCount == null ? null : rowCount.longValue(),
                resultSet.getBoolean("truncated"),
                resultSet.getString("error_message"));
    }
}
