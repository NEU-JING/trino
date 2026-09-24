package io.trino.datafabric.dataset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class DatasetRepository
{
    private static final String SELECT = "SELECT id, uid, name, description, domain, owner, kind, status, "
            + "current_version, materialization_mode, definition_json, created_at, updated_at FROM dataset";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DatasetRepository(JdbcTemplate jdbcTemplate)
    {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Dataset> findById(long id)
    {
        return jdbcTemplate.query(SELECT + " WHERE id = ?", this::mapDataset, id).stream().findFirst();
    }

    public Optional<Dataset> findByUid(String uid)
    {
        return jdbcTemplate.query(SELECT + " WHERE uid = ?", this::mapDataset, uid).stream().findFirst();
    }

    public Optional<Dataset> findByName(String name)
    {
        return jdbcTemplate.query(SELECT + " WHERE name = ?", this::mapDataset, name).stream().findFirst();
    }

    public List<Dataset> findAll()
    {
        return jdbcTemplate.query(SELECT + " ORDER BY domain, name", this::mapDataset);
    }

    public long insert(Dataset dataset)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO dataset (uid, name, description, domain, owner, kind, status, current_version, "
                            + "materialization_mode, definition_json, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, dataset.uid());
            statement.setString(2, dataset.name());
            statement.setString(3, dataset.description());
            statement.setString(4, dataset.domain());
            statement.setString(5, dataset.owner());
            statement.setString(6, dataset.kind().name());
            statement.setString(7, dataset.status().name());
            statement.setInt(8, dataset.currentVersion());
            statement.setString(9, dataset.materializationMode().name());
            statement.setString(10, dataset.definitionJson());
            statement.setTimestamp(11, Timestamp.from(dataset.createdAt()));
            statement.setTimestamp(12, Timestamp.from(dataset.updatedAt()));
            return statement;
        }, keyHolder);
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys == null || keys.isEmpty()) {
            throw new IllegalStateException("No generated key for dataset " + dataset.name());
        }
        return ((Number) keys.values().iterator().next()).longValue();
    }

    public void updateDefinition(long id, String definitionJson, Instant updatedAt)
    {
        jdbcTemplate.update(
                "UPDATE dataset SET definition_json = ?, updated_at = ? WHERE id = ?",
                definitionJson, Timestamp.from(updatedAt), id);
    }

    public void updateStatus(long id, DatasetStatus status, Instant updatedAt)
    {
        jdbcTemplate.update(
                "UPDATE dataset SET status = ?, updated_at = ? WHERE id = ?",
                status.name(), Timestamp.from(updatedAt), id);
    }

    public void updateMaterializationMode(long id, MaterializationMode mode, Instant updatedAt)
    {
        jdbcTemplate.update(
                "UPDATE dataset SET materialization_mode = ?, updated_at = ? WHERE id = ?",
                mode.name(), Timestamp.from(updatedAt), id);
    }

    public void updateCurrentVersion(long id, int version, Instant updatedAt)
    {
        jdbcTemplate.update(
                "UPDATE dataset SET current_version = ?, updated_at = ? WHERE id = ?",
                version, Timestamp.from(updatedAt), id);
    }

    public void delete(long id)
    {
        jdbcTemplate.update("DELETE FROM dataset_field WHERE dataset_id = ?", id);
        jdbcTemplate.update("DELETE FROM dataset_version WHERE dataset_id = ?", id);
        jdbcTemplate.update("DELETE FROM dataset_materialization WHERE dataset_id = ?", id);
        jdbcTemplate.update("DELETE FROM dataset WHERE id = ?", id);
    }

    public List<DatasetField> fields(long datasetId)
    {
        return jdbcTemplate.query(
                "SELECT id, dataset_id, name, label, data_type, role, aggregation, time_grain, format, unit, "
                        + "nullable, ordinal FROM dataset_field WHERE dataset_id = ? ORDER BY ordinal, name",
                this::mapField,
                datasetId);
    }

    public void replaceFields(long datasetId, List<DatasetField> fields)
    {
        jdbcTemplate.update("DELETE FROM dataset_field WHERE dataset_id = ?", datasetId);
        for (DatasetField field : fields) {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO dataset_field (dataset_id, name, label, data_type, role, aggregation, "
                                + "time_grain, format, unit, nullable, ordinal) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                statement.setLong(1, datasetId);
                statement.setString(2, field.name());
                statement.setString(3, field.label());
                statement.setString(4, field.dataType());
                statement.setString(5, field.role().name());
                setNullableString(statement, 6, field.aggregation());
                setNullableString(statement, 7, field.timeGrain());
                setNullableString(statement, 8, field.format());
                setNullableString(statement, 9, field.unit());
                statement.setBoolean(10, field.nullable());
                statement.setInt(11, field.ordinal());
                return statement;
            });
        }
    }

    public List<DatasetVersion> versions(long datasetId)
    {
        return jdbcTemplate.query(
                "SELECT id, dataset_id, version, status, definition_json, fields_json, published_at, created_at "
                        + "FROM dataset_version WHERE dataset_id = ? ORDER BY version DESC",
                this::mapVersion,
                datasetId);
    }

    public Optional<DatasetVersion> findVersion(long datasetId, int version)
    {
        return jdbcTemplate.query(
                "SELECT id, dataset_id, version, status, definition_json, fields_json, published_at, created_at "
                        + "FROM dataset_version WHERE dataset_id = ? AND version = ?",
                this::mapVersion,
                datasetId,
                version).stream().findFirst();
    }

    public long insertVersion(DatasetVersion version)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO dataset_version (dataset_id, version, status, definition_json, fields_json, "
                            + "published_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, version.datasetId());
            statement.setInt(2, version.version());
            statement.setString(3, version.status().name());
            statement.setString(4, version.definitionJson());
            statement.setString(5, version.fieldsJson());
            if (version.publishedAt() == null) {
                statement.setNull(6, Types.TIMESTAMP);
            }
            else {
                statement.setTimestamp(6, Timestamp.from(version.publishedAt()));
            }
            statement.setTimestamp(7, Timestamp.from(version.createdAt()));
            return statement;
        }, keyHolder);
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys == null || keys.isEmpty()) {
            throw new IllegalStateException("No generated key for dataset version");
        }
        return ((Number) keys.values().iterator().next()).longValue();
    }

    public Optional<Materialization> materialization(long datasetId)
    {
        return jdbcTemplate.query(
                "SELECT id, dataset_id, mode, target, status, message, refreshed_at, stale_seconds "
                        + "FROM dataset_materialization WHERE dataset_id = ?",
                this::mapMaterialization,
                datasetId).stream().findFirst();
    }

    public void upsertMaterialization(Materialization materialization)
    {
        int updated = jdbcTemplate.update(
                "UPDATE dataset_materialization SET mode = ?, target = ?, status = ?, message = ?, "
                        + "refreshed_at = ?, stale_seconds = ? WHERE dataset_id = ?",
                materialization.mode().name(),
                materialization.target(),
                materialization.status(),
                materialization.message(),
                materialization.refreshedAt() == null ? null : Timestamp.from(materialization.refreshedAt()),
                materialization.staleSeconds(),
                materialization.datasetId());
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO dataset_materialization (dataset_id, mode, target, status, message, refreshed_at, stale_seconds) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    materialization.datasetId(),
                    materialization.mode().name(),
                    materialization.target(),
                    materialization.status(),
                    materialization.message(),
                    materialization.refreshedAt() == null ? null : Timestamp.from(materialization.refreshedAt()),
                    materialization.staleSeconds());
        }
    }

    public String writeJson(Object value)
    {
        try {
            return objectMapper.writeValueAsString(value);
        }
        catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize dataset definition", e);
        }
    }

    public DatasetDefinition readDefinition(String json)
    {
        try {
            return objectMapper.readValue(json, DatasetDefinition.class);
        }
        catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to parse dataset definition", e);
        }
    }

    public List<DatasetField> readFields(String json)
    {
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, DatasetField.class));
        }
        catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to parse dataset fields", e);
        }
    }

    private Dataset mapDataset(ResultSet resultSet, int rowNum)
            throws SQLException
    {
        return new Dataset(
                resultSet.getLong("id"),
                resultSet.getString("uid"),
                resultSet.getString("name"),
                resultSet.getString("description"),
                resultSet.getString("domain"),
                resultSet.getString("owner"),
                DatasetKind.valueOf(resultSet.getString("kind")),
                DatasetStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("current_version"),
                MaterializationMode.valueOf(resultSet.getString("materialization_mode")),
                resultSet.getString("definition_json"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    private DatasetField mapField(ResultSet resultSet, int rowNum)
            throws SQLException
    {
        return new DatasetField(
                resultSet.getLong("id"),
                resultSet.getLong("dataset_id"),
                resultSet.getString("name"),
                resultSet.getString("label"),
                resultSet.getString("data_type"),
                FieldRole.valueOf(resultSet.getString("role")),
                resultSet.getString("aggregation"),
                resultSet.getString("time_grain"),
                resultSet.getString("format"),
                resultSet.getString("unit"),
                resultSet.getBoolean("nullable"),
                resultSet.getInt("ordinal"));
    }

    private DatasetVersion mapVersion(ResultSet resultSet, int rowNum)
            throws SQLException
    {
        Timestamp publishedAt = resultSet.getTimestamp("published_at");
        return new DatasetVersion(
                resultSet.getLong("id"),
                resultSet.getLong("dataset_id"),
                resultSet.getInt("version"),
                DatasetStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("definition_json"),
                resultSet.getString("fields_json"),
                publishedAt == null ? null : publishedAt.toInstant(),
                resultSet.getTimestamp("created_at").toInstant());
    }

    private Materialization mapMaterialization(ResultSet resultSet, int rowNum)
            throws SQLException
    {
        Timestamp refreshedAt = resultSet.getTimestamp("refreshed_at");
        long staleSeconds = resultSet.getLong("stale_seconds");
        return new Materialization(
                resultSet.getLong("id"),
                resultSet.getLong("dataset_id"),
                MaterializationMode.valueOf(resultSet.getString("mode")),
                resultSet.getString("target"),
                resultSet.getString("status"),
                resultSet.getString("message"),
                refreshedAt == null ? null : refreshedAt.toInstant(),
                resultSet.wasNull() ? null : staleSeconds);
    }

    private static void setNullableString(PreparedStatement statement, int index, String value)
            throws SQLException
    {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        }
        else {
            statement.setString(index, value);
        }
    }
}
