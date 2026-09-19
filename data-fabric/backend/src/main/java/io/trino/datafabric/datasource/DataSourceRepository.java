package io.trino.datafabric.datasource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class DataSourceRepository
{
    private static final String SELECT = "SELECT id, name, business_type, connector_name, properties_json, enabled, created_by, created_at FROM data_source";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DataSourceRepository(JdbcTemplate jdbcTemplate)
    {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<DataSource> findByName(String name)
    {
        return jdbcTemplate.query(SELECT + " WHERE name = ?", this::mapRow, name).stream().findFirst();
    }

    public Optional<DataSource> findById(long id)
    {
        return jdbcTemplate.query(SELECT + " WHERE id = ?", this::mapRow, id).stream().findFirst();
    }

    public List<DataSource> findAll()
    {
        return jdbcTemplate.query(SELECT + " ORDER BY name", this::mapRow);
    }

    public long insert(DataSource dataSource)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO data_source (name, business_type, connector_name, properties_json, enabled, created_by, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, dataSource.name());
            statement.setString(2, dataSource.businessType().name());
            statement.setString(3, dataSource.connectorName());
            statement.setString(4, writeConnection(dataSource.connection()));
            statement.setBoolean(5, dataSource.enabled());
            statement.setString(6, dataSource.createdBy());
            statement.setTimestamp(7, Timestamp.from(dataSource.createdAt()));
            return statement;
        }, keyHolder);

        Map<String, Object> keys = keyHolder.getKeys();
        if (keys == null || keys.isEmpty()) {
            throw new IllegalStateException("No generated key for data source " + dataSource.name());
        }
        return ((Number) keys.values().iterator().next()).longValue();
    }

    public void updateConnection(long id, BusinessType businessType, String connectorName, DataSourceConnection connection)
    {
        jdbcTemplate.update(
                "UPDATE data_source SET business_type = ?, connector_name = ?, properties_json = ?, enabled = TRUE WHERE id = ?",
                businessType.name(), connectorName, writeConnection(connection), id);
    }

    public void setEnabled(long id, boolean enabled)
    {
        jdbcTemplate.update("UPDATE data_source SET enabled = ? WHERE id = ?", enabled, id);
    }

    private DataSource mapRow(java.sql.ResultSet resultSet, int rowNum)
            throws java.sql.SQLException
    {
        return new DataSource(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                BusinessType.from(resultSet.getString("business_type")),
                resultSet.getString("connector_name"),
                readConnection(resultSet.getString("properties_json")),
                resultSet.getBoolean("enabled"),
                resultSet.getString("created_by"),
                resultSet.getTimestamp("created_at").toInstant());
    }

    private DataSourceConnection readConnection(String json)
    {
        try {
            return objectMapper.readValue(json, DataSourceConnection.class);
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid data source connection JSON", e);
        }
    }

    private String writeConnection(DataSourceConnection connection)
    {
        try {
            return objectMapper.writeValueAsString(connection);
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize data source connection", e);
        }
    }
}
