package io.trino.datafabric.dataset;

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
public class DatasetRelationRepository
{
    private static final String SELECT = "SELECT id, from_dataset_uid, from_field, to_dataset_uid, to_field, "
            + "join_type, cardinality, origin, created_by, created_at FROM dataset_relation";

    private final JdbcTemplate jdbcTemplate;

    public DatasetRelationRepository(JdbcTemplate jdbcTemplate)
    {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DatasetRelation> findAll()
    {
        return jdbcTemplate.query(SELECT + " ORDER BY from_dataset_uid, to_dataset_uid", this::mapRow);
    }

    public List<DatasetRelation> findByDatasetUid(String uid)
    {
        return jdbcTemplate.query(
                SELECT + " WHERE from_dataset_uid = ? OR to_dataset_uid = ? ORDER BY from_dataset_uid, to_dataset_uid",
                this::mapRow,
                uid,
                uid);
    }

    public Optional<DatasetRelation> findExisting(String fromUid, String fromField, String toUid, String toField, String joinType)
    {
        return jdbcTemplate.query(
                SELECT + " WHERE from_dataset_uid = ? AND from_field = ? AND to_dataset_uid = ? AND to_field = ? AND join_type = ?",
                this::mapRow,
                fromUid,
                fromField,
                toUid,
                toField,
                joinType).stream().findFirst();
    }

    public Optional<DatasetRelation> findById(long id)
    {
        return jdbcTemplate.query(SELECT + " WHERE id = ?", this::mapRow, id).stream().findFirst();
    }

    public long insert(DatasetRelation relation)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO dataset_relation (from_dataset_uid, from_field, to_dataset_uid, to_field, "
                            + "join_type, cardinality, origin, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, relation.fromDatasetUid());
            statement.setString(2, relation.fromField());
            statement.setString(3, relation.toDatasetUid());
            statement.setString(4, relation.toField());
            statement.setString(5, relation.joinType());
            statement.setString(6, relation.cardinality());
            statement.setString(7, relation.origin());
            statement.setString(8, relation.createdBy());
            statement.setTimestamp(9, Timestamp.from(relation.createdAt()));
            return statement;
        }, keyHolder);
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys == null || keys.isEmpty()) {
            throw new IllegalStateException("No generated key for dataset relation");
        }
        return ((Number) keys.values().iterator().next()).longValue();
    }

    public void confirm(long id)
    {
        jdbcTemplate.update("UPDATE dataset_relation SET origin = ? WHERE id = ?", DatasetRelation.DECLARED, id);
    }

    public void delete(long id)
    {
        jdbcTemplate.update("DELETE FROM dataset_relation WHERE id = ?", id);
    }

    private DatasetRelation mapRow(ResultSet resultSet, int rowNum)
            throws SQLException
    {
        return new DatasetRelation(
                resultSet.getLong("id"),
                resultSet.getString("from_dataset_uid"),
                resultSet.getString("from_field"),
                resultSet.getString("to_dataset_uid"),
                resultSet.getString("to_field"),
                resultSet.getString("join_type"),
                resultSet.getString("cardinality"),
                resultSet.getString("origin"),
                resultSet.getString("created_by"),
                resultSet.getTimestamp("created_at").toInstant());
    }
}
