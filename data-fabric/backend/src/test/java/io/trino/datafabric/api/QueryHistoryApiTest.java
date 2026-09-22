package io.trino.datafabric.api;

import io.trino.datafabric.query.QueryEngine;
import io.trino.datafabric.query.QueryResult;
import io.trino.datafabric.support.ApiClient;
import io.trino.datafabric.support.FakeQueryEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(QueryHistoryApiTest.FakeConfig.class)
class QueryHistoryApiTest
{
    @TestConfiguration
    static class FakeConfig
    {
        @Bean
        @Primary
        FakeQueryEngine fakeQueryEngine()
        {
            return new FakeQueryEngine();
        }
    }

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private QueryEngine queryEngine;

    private ApiClient client;
    private FakeQueryEngine fake;

    @BeforeEach
    void setUp()
    {
        client = new ApiClient(port);
        fake = (FakeQueryEngine) queryEngine;
        fake.reset();
        jdbcTemplate.update("DELETE FROM query_history");
        jdbcTemplate.update("DELETE FROM saved_query");
    }

    @Test
    void executedQueryIsRecordedInHistory()
    {
        fake.setResult("SELECT history_me", new QueryResult(List.of("x"), List.of(List.of(1), List.of(2)), false));
        String token = bearer(login("viewer", "viewer"));
        startAndWait(token, "SELECT history_me");

        ApiClient.Response history = client.get("/api/query-history", token);
        assertThat(history.status()).isEqualTo(200);
        assertThat(history.body()).contains("history_me").contains("\"state\":\"FINISHED\"").contains("\"rowCount\":2");
    }

    @Test
    void failedQueryIsRecordedWithError()
    {
        fake.setFailure("SELECT broken", "mismatched input");
        String token = bearer(login("viewer", "viewer"));
        startAndWait(token, "SELECT broken");

        ApiClient.Response history = client.get("/api/query-history", token);
        assertThat(history.body()).contains("\"state\":\"FAILED\"").contains("mismatched input");
    }

    @Test
    void historyIsIsolatedPerUser()
    {
        fake.setResult("SELECT viewer_sql", new QueryResult(List.of("x"), List.of(List.of(1)), false));
        fake.setResult("SELECT admin_sql", new QueryResult(List.of("x"), List.of(List.of(1)), false));
        startAndWait(bearer(login("viewer", "viewer")), "SELECT viewer_sql");
        startAndWait(bearer(login("admin", "admin")), "SELECT admin_sql");

        ApiClient.Response viewerHistory = client.get("/api/query-history", bearer(login("viewer", "viewer")));
        assertThat(viewerHistory.body()).contains("viewer_sql").doesNotContain("admin_sql");

        ApiClient.Response adminHistory = client.get("/api/query-history", bearer(login("admin", "admin")));
        assertThat(adminHistory.body()).contains("admin_sql").doesNotContain("viewer_sql");
    }

    @Test
    void anonymousCannotReadHistory()
    {
        assertThat(client.get("/api/query-history", null).status()).isEqualTo(401);
    }

    @Test
    void savedQueryRoundTrip()
    {
        String token = bearer(login("viewer", "viewer"));
        ApiClient.Response created = client.post("/api/saved-queries", token, "{\"name\":\"日报\",\"sql\":\"SELECT 1\"}");
        assertThat(created.status()).isEqualTo(201);
        long id = extractLong(created.body(), "id");

        ApiClient.Response listed = client.get("/api/saved-queries", token);
        assertThat(listed.status()).isEqualTo(200);
        assertThat(listed.body()).contains("日报").contains("SELECT 1");

        assertThat(client.delete("/api/saved-queries/" + id, token).status()).isEqualTo(204);
        assertThat(client.get("/api/saved-queries", token).body()).doesNotContain("日报");
        assertThat(client.delete("/api/saved-queries/" + id, token).status()).isEqualTo(404);
    }

    @Test
    void savingSameNameUpdatesInPlace()
    {
        String token = bearer(login("viewer", "viewer"));
        client.post("/api/saved-queries", token, "{\"name\":\"mine\",\"sql\":\"SELECT 1\"}");
        client.post("/api/saved-queries", token, "{\"name\":\"mine\",\"sql\":\"SELECT 2\"}");

        ApiClient.Response listed = client.get("/api/saved-queries", token);
        assertThat(listed.body()).contains("SELECT 2").doesNotContain("SELECT 1");
        assertThat(countOccurrences(listed.body(), "\"name\":\"mine\"")).isEqualTo(1);
    }

    @Test
    void savedQueriesAreIsolatedPerUser()
    {
        client.post("/api/saved-queries", bearer(login("admin", "admin")), "{\"name\":\"admin_only\",\"sql\":\"SELECT 1\"}");
        assertThat(client.get("/api/saved-queries", bearer(login("viewer", "viewer"))).body())
                .doesNotContain("admin_only");
    }

    @Test
    void savedQueryRequiresNameAndSql()
    {
        String token = bearer(login("viewer", "viewer"));
        assertThat(client.post("/api/saved-queries", token, "{\"name\":\"\",\"sql\":\"SELECT 1\"}").status()).isEqualTo(400);
        assertThat(client.post("/api/saved-queries", token, "{\"name\":\"x\",\"sql\":\"  \"}").status()).isEqualTo(400);
    }

    @Test
    void anonymousCannotUseSavedQueries()
    {
        assertThat(client.get("/api/saved-queries", null).status()).isEqualTo(401);
    }

    private ApiClient.Response startAndWait(String token, String sql)
    {
        ApiClient.Response start = client.post("/api/queries", token, "{\"sql\":\"" + sql + "\"}");
        assertThat(start.status()).isEqualTo(202);
        String queryId = extractString(start.body(), "queryId");
        for (int i = 0; i < 60; i++) {
            ApiClient.Response response = client.get("/api/queries/" + queryId, token);
            if (!response.body().contains("\"state\":\"RUNNING\"")) {
                return response;
            }
            sleep();
        }
        throw new AssertionError("query did not finish");
    }

    private String login(String username, String password)
    {
        ApiClient.Response response = client.post(
                "/api/auth/login", null, "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        assertThat(response.status()).isEqualTo(200);
        return extractString(response.body(), "token");
    }

    private static String bearer(String token)
    {
        return "Bearer " + token;
    }

    private static String extractString(String body, String field)
    {
        String marker = "\"" + field + "\":\"";
        int start = body.indexOf(marker) + marker.length();
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }

    private static long extractLong(String body, String field)
    {
        String marker = "\"" + field + "\":";
        int start = body.indexOf(marker) + marker.length();
        int end = start;
        while (end < body.length() && Character.isDigit(body.charAt(end))) {
            end++;
        }
        return Long.parseLong(body.substring(start, end));
    }

    private static int countOccurrences(String body, String needle)
    {
        int count = 0;
        int index = body.indexOf(needle);
        while (index >= 0) {
            count++;
            index = body.indexOf(needle, index + needle.length());
        }
        return count;
    }

    private static void sleep()
    {
        try {
            Thread.sleep(50);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
