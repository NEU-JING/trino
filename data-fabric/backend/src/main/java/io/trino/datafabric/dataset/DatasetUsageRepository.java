package io.trino.datafabric.dataset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class DatasetUsageRepository
{
    private static final String SELECT = "SELECT id, dataset_uid, application, username, query_count, "
            + "failure_count, total_latency_ms, last_used_at FROM dataset_usage";

    private final JdbcTemplate jdbcTemplate;

    public DatasetUsageRepository(JdbcTemplate jdbcTemplate)
    {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(String datasetUid, String application, String username, long latencyMs, boolean failed)
    {
        String app = application == null || application.isBlank() ? "unknown" : application;
        String user = username == null || username.isBlank() ? "unknown" : username;
        Instant now = Instant.now();
        int updated = jdbcTemplate.update(
                "UPDATE dataset_usage SET query_count = query_count + 1, failure_count = failure_count + ?, "
                        + "total_latency_ms = total_latency_ms + ?, last_used_at = ? "
                        + "WHERE dataset_uid = ? AND application = ? AND username = ?",
                failed ? 1 : 0,
                latencyMs,
                Timestamp.from(now),
                datasetUid,
                app,
                user);
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO dataset_usage (dataset_uid, application, username, query_count, failure_count, "
                            + "total_latency_ms, last_used_at) VALUES (?, ?, ?, 1, ?, ?, ?)",
                    datasetUid,
                    app,
                    user,
                    failed ? 1 : 0,
                    latencyMs,
                    Timestamp.from(now));
        }
    }

    public List<DatasetUsage> findByDatasetUid(String datasetUid)
    {
        return jdbcTemplate.query(
                SELECT + " WHERE dataset_uid = ? ORDER BY query_count DESC",
                this::mapRow,
                datasetUid);
    }

    public List<DatasetUsage> findAll()
    {
        return jdbcTemplate.query(SELECT + " ORDER BY query_count DESC", this::mapRow);
    }

    private DatasetUsage mapRow(ResultSet resultSet, int rowNum)
            throws SQLException
    {
        Timestamp lastUsedAt = resultSet.getTimestamp("last_used_at");
        return new DatasetUsage(
                resultSet.getLong("id"),
                resultSet.getString("dataset_uid"),
                resultSet.getString("application"),
                resultSet.getString("username"),
                resultSet.getLong("query_count"),
                resultSet.getLong("failure_count"),
                resultSet.getLong("total_latency_ms"),
                lastUsedAt == null ? null : lastUsedAt.toInstant());
    }
}
