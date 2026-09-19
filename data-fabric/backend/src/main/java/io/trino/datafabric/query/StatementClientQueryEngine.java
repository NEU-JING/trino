package io.trino.datafabric.query;

import io.trino.client.ClientSession;
import io.trino.client.Column;
import io.trino.client.QueryStatusInfo;
import io.trino.client.StatementClient;
import io.trino.client.StatementClientFactory;
import io.trino.datafabric.trino.TrinoQueryException;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class StatementClientQueryEngine
        implements QueryEngine
{
    private final OkHttpClient httpClient = new OkHttpClient();
    private final String serverUrl;
    private final String serviceUser;

    public StatementClientQueryEngine(
            @Value("${data-fabric.trino.url:http://localhost:8080}") String serverUrl,
            @Value("${data-fabric.trino.user:trino_service}") String serviceUser)
    {
        this.serverUrl = serverUrl;
        this.serviceUser = serviceUser;
    }

    @Override
    public Handle start(String sql, String user, int maxRows)
    {
        String actingUser = (user == null || user.isBlank()) ? serviceUser : user;
        ClientSession session = ClientSession.builder()
                .server(URI.create(serverUrl))
                .user(Optional.of(actingUser))
                .source("data-fabric-platform")
                .timeZone(ZoneId.of("UTC"))
                .locale(Locale.US)
                .build();
        StatementClient statementClient = StatementClientFactory.newStatementClient(httpClient, session, sql);

        return new Handle()
        {
            @Override
            public QueryResult await()
            {
                List<String> columns = List.of();
                List<List<Object>> rows = new ArrayList<>();
                boolean truncated = false;

                while (statementClient.isRunning()) {
                    QueryStatusInfo status = statementClient.currentStatusInfo();
                    if (columns.isEmpty() && status.getColumns() != null && !status.getColumns().isEmpty()) {
                        columns = columnNames(status.getColumns());
                    }
                    for (List<Object> row : statementClient.currentRows()) {
                        if (rows.size() >= maxRows) {
                            truncated = true;
                            break;
                        }
                        rows.add(List.copyOf(row));
                    }
                    if (truncated) {
                        break;
                    }
                    statementClient.advance();
                }

                QueryStatusInfo finalStatus = statementClient.finalStatusInfo();
                if (finalStatus.getError() != null) {
                    statementClient.close();
                    throw new TrinoQueryException(finalStatus.getError().getMessage());
                }
                if (columns.isEmpty() && finalStatus.getColumns() != null && !finalStatus.getColumns().isEmpty()) {
                    columns = columnNames(finalStatus.getColumns());
                }
                statementClient.close();
                return new QueryResult(columns, rows, truncated);
            }

            @Override
            public void cancel()
            {
                try {
                    statementClient.cancelLeafStage();
                }
                catch (RuntimeException ignored) {
                    // best effort
                }
                statementClient.close();
            }
        };
    }

    private static List<String> columnNames(List<Column> columns)
    {
        return columns.stream().map(Column::getName).toList();
    }
}
