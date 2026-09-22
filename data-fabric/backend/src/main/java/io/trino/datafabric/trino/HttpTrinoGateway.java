package io.trino.datafabric.trino;

import io.trino.client.ClientSession;
import io.trino.client.Column;
import io.trino.client.QueryError;
import io.trino.client.QueryStatusInfo;
import io.trino.client.StatementClient;
import io.trino.client.StatementClientFactory;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class HttpTrinoGateway
        implements TrinoGateway
{
    private final OkHttpClient httpClient = new OkHttpClient();
    private final String serverUrl;
    private final String serviceUser;

    public HttpTrinoGateway(
            @Value("${data-fabric.trino.url:http://localhost:8080}") String serverUrl,
            @Value("${data-fabric.trino.user:trino_service}") String serviceUser)
    {
        this.serverUrl = serverUrl;
        this.serviceUser = serviceUser;
    }

    @Override
    public QueryResult execute(String sql, String actingUser)
    {
        String user = (actingUser == null || actingUser.isBlank()) ? serviceUser : actingUser;
        ClientSession session = ClientSession.builder()
                .server(URI.create(serverUrl))
                .user(Optional.of(user))
                .source("data-fabric-platform")
                .timeZone(ZoneId.of("UTC"))
                .locale(Locale.US)
                .build();

        List<String> columns = List.of();
        List<List<Object>> rows = new ArrayList<>();
        try (StatementClient client = StatementClientFactory.newStatementClient(httpClient, session, sql)) {
            while (client.isRunning()) {
                QueryStatusInfo status = client.currentStatusInfo();
                if (columns.isEmpty() && status.getColumns() != null && !status.getColumns().isEmpty()) {
                    columns = status.getColumns().stream().map(Column::getName).toList();
                }
                for (List<Object> row : client.currentRows()) {
                    rows.add(new ArrayList<>(row));
                }
                client.advance();
            }

            QueryStatusInfo finalStatus = client.finalStatusInfo();
            QueryError error = finalStatus.getError();
            if (error != null) {
                throw new TrinoQueryException(error.getMessage());
            }
            if (columns.isEmpty() && finalStatus.getColumns() != null && !finalStatus.getColumns().isEmpty()) {
                columns = finalStatus.getColumns().stream().map(Column::getName).toList();
            }
            return new QueryResult(columns, rows);
        }
    }

    @Override
    public void createCatalog(String catalog, String connector, Map<String, String> properties)
    {
        StringBuilder sql = new StringBuilder("CREATE CATALOG ")
                .append(catalog)
                .append(" USING ")
                .append(connector)
                .append(" WITH (");
        boolean first = true;
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (!first) {
                sql.append(", ");
            }
            first = false;
            sql.append(quoteIdentifier(entry.getKey()))
                    .append(" = '")
                    .append(entry.getValue().replace("'", "''"))
                    .append("'");
        }
        sql.append(")");
        execute(sql.toString(), null);
    }

    @Override
    public void dropCatalog(String catalog)
    {
        execute("DROP CATALOG IF EXISTS " + catalog, null);
    }

    @Override
    public void probeCatalog(String catalog)
    {
        execute("SHOW SCHEMAS FROM " + catalog, null);
    }

    /** Catalog and connector names are validated by the service; only property keys need quoting. */
    private static String quoteIdentifier(String identifier)
    {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }
}
