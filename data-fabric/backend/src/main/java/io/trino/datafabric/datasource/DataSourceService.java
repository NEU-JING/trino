package io.trino.datafabric.datasource;

import io.trino.datafabric.trino.TrinoGateway;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Service
public class DataSourceService
{
    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z][a-z0-9_]{0,62}");

    private final DataSourceRepository repository;
    private final TrinoGateway trinoGateway;

    public DataSourceService(DataSourceRepository repository, TrinoGateway trinoGateway)
    {
        this.repository = repository;
        this.trinoGateway = trinoGateway;
    }

    public DataSourceView register(String actor, DataSourceRequest request)
    {
        String name = normalizeName(request.name());
        BusinessType businessType = BusinessType.from(request.businessType());
        DataSourceConnection connection = validateConnection(businessType, request);
        if (repository.findByName(name).isPresent()) {
            throw new DuplicateDataSourceException(name);
        }

        Map<String, String> properties = connectorProperties(businessType, connection);
        try {
            trinoGateway.createCatalog(name, businessType.connectorName(), properties);
            trinoGateway.probeCatalog(name);
        }
        catch (RuntimeException e) {
            dropQuietly(name);
            throw new DataSourceException("Data source connection validation failed: " + e.getMessage());
        }

        long id = repository.insert(new DataSource(
                0, name, businessType, businessType.connectorName(), connection, true, actor, Instant.now()));
        return toView(repository.findById(id).orElseThrow());
    }

    public void testConnection(DataSourceRequest request)
    {
        BusinessType businessType = BusinessType.from(request.businessType());
        DataSourceConnection connection = validateConnection(businessType, request);
        verifyReachable(businessType, connectorProperties(businessType, connection));
    }

    public List<DataSourceView> list()
    {
        return repository.findAll().stream().map(DataSourceService::toView).toList();
    }

    public DataSourceView get(long id)
    {
        return toView(repository.findById(id).orElseThrow(() -> new DataSourceNotFoundException(id)));
    }

    public DataSourceView update(long id, DataSourceRequest request)
    {
        DataSource existing = repository.findById(id).orElseThrow(() -> new DataSourceNotFoundException(id));
        BusinessType businessType = BusinessType.from(request.businessType());
        DataSourceConnection connection = validateConnection(businessType, request);
        Map<String, String> properties = connectorProperties(businessType, connection);

        verifyReachable(businessType, properties);

        trinoGateway.dropCatalog(existing.name());
        trinoGateway.createCatalog(existing.name(), businessType.connectorName(), properties);
        repository.updateConnection(id, businessType, businessType.connectorName(), connection);
        return get(id);
    }

    public DataSourceView disable(long id)
    {
        DataSource existing = repository.findById(id).orElseThrow(() -> new DataSourceNotFoundException(id));
        trinoGateway.dropCatalog(existing.name());
        repository.setEnabled(id, false);
        return get(id);
    }

    private void verifyReachable(BusinessType businessType, Map<String, String> properties)
    {
        String probeName = "df_probe_" + Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 16);
        try {
            trinoGateway.createCatalog(probeName, businessType.connectorName(), properties);
            trinoGateway.probeCatalog(probeName);
        }
        catch (RuntimeException e) {
            throw new DataSourceException("Data source connection validation failed: " + e.getMessage());
        }
        finally {
            dropQuietly(probeName);
        }
    }

    private void dropQuietly(String catalog)
    {
        try {
            trinoGateway.dropCatalog(catalog);
        }
        catch (RuntimeException ignored) {
            // best effort cleanup
        }
    }

    private static DataSourceConnection validateConnection(BusinessType businessType, DataSourceRequest request)
    {
        if (request.host() == null || request.host().isBlank()) {
            throw new IllegalArgumentException("host is required");
        }
        if (request.port() == null || request.port() < 1 || request.port() > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        if (request.user() == null || request.user().isBlank()) {
            throw new IllegalArgumentException("user is required");
        }
        if (request.password() == null) {
            throw new IllegalArgumentException("password is required");
        }
        String database = request.database() == null ? "" : request.database();
        if (businessType == BusinessType.GREENPLUM && database.isBlank()) {
            throw new IllegalArgumentException("database is required for Greenplum");
        }
        return new DataSourceConnection(request.host(), request.port(), database, request.user(), request.password());
    }

    private static Map<String, String> connectorProperties(BusinessType businessType, DataSourceConnection connection)
    {
        Map<String, String> properties = new LinkedHashMap<>();
        switch (businessType) {
            case OCEANBASE -> properties.put(
                    "connection-url",
                    "jdbc:mysql://%s:%d?useSSL=false&allowPublicKeyRetrieval=true".formatted(connection.host(), connection.port()));
            case GREENPLUM -> properties.put(
                    "connection-url",
                    "jdbc:postgresql://%s:%d/%s".formatted(connection.host(), connection.port(), connection.database()));
            case DAMENG -> properties.put(
                    "connection-url",
                    "jdbc:dm://%s:%d".formatted(connection.host(), connection.port()));
        }
        properties.put("connection-user", connection.user());
        properties.put("connection-password", connection.password());
        return properties;
    }

    private static String normalizeName(String name)
    {
        if (name == null) {
            throw new IllegalArgumentException("name is required");
        }
        String normalized = name.trim().toLowerCase(java.util.Locale.ROOT);
        if (!NAME_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "name must match [a-z][a-z0-9_]* and be at most 63 characters");
        }
        return normalized;
    }

    private static DataSourceView toView(DataSource dataSource)
    {
        DataSourceConnection connection = dataSource.connection();
        return new DataSourceView(
                dataSource.id(),
                dataSource.name(),
                dataSource.businessType().displayName(),
                connection.host(),
                connection.port(),
                connection.database(),
                connection.user(),
                dataSource.enabled(),
                dataSource.createdBy(),
                dataSource.createdAt());
    }
}
