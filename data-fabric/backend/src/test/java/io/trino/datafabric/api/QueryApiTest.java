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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "data-fabric.query.timeout=1s")
@Import(QueryApiTest.FakeConfig.class)
class QueryApiTest
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
    private QueryEngine queryEngine;

    private ApiClient client;
    private FakeQueryEngine fake;

    @BeforeEach
    void setUp()
    {
        client = new ApiClient(port);
        fake = (FakeQueryEngine) queryEngine;
        fake.reset();
    }

    @Test
    void resultIsReturned()
    {
        fake.setResult("SELECT 1", new QueryResult(List.of("x"), List.of(List.of(1)), false));
        String token = bearer(login("viewer", "viewer"));

        ApiClient.Response start = client.post("/api/queries", token, sql("SELECT 1"));
        assertThat(start.status()).isEqualTo(202);
        String queryId = extractString(start.body(), "queryId");

        ApiClient.Response view = poll(token, queryId);
        assertThat(view.body()).contains("\"state\":\"FINISHED\"").contains("\"x\"").contains("1");
    }

    @Test
    void errorIsReadableAndQueryCanContinue()
    {
        fake.setFailure("bad sql", "line 1:1: mismatched input 'x'");
        String token = bearer(login("viewer", "viewer"));

        ApiClient.Response start = client.post("/api/queries", token, sql("bad sql"));
        ApiClient.Response view = poll(token, extractString(start.body(), "queryId"));
        assertThat(view.body()).contains("\"state\":\"FAILED\"").contains("mismatched input");

        // a subsequent query still works
        fake.setResult("SELECT 2", new QueryResult(List.of("y"), List.of(List.of(2)), false));
        ApiClient.Response next = client.post("/api/queries", token, sql("SELECT 2"));
        assertThat(poll(token, extractString(next.body(), "queryId")).body()).contains("\"state\":\"FINISHED\"");
    }

    @Test
    void emptyResultKeepsColumns()
    {
        fake.setResult("SELECT x FROM t WHERE false", new QueryResult(List.of("x"), List.of(), false));
        String token = bearer(login("viewer", "viewer"));

        ApiClient.Response start = client.post("/api/queries", token, sql("SELECT x FROM t WHERE false"));
        ApiClient.Response view = poll(token, extractString(start.body(), "queryId"));
        assertThat(view.body()).contains("\"state\":\"FINISHED\"").contains("\"x\"").contains("\"rows\":[]");
    }

    @Test
    void runningQueryCanBeCanceled()
    {
        fake.setBlocking(true);
        String token = bearer(login("viewer", "viewer"));

        ApiClient.Response start = client.post("/api/queries", token, sql("SELECT slow"));
        String queryId = extractString(start.body(), "queryId");

        ApiClient.Response canceled = client.post("/api/queries/" + queryId + "/cancel", token, null);
        assertThat(canceled.status()).isEqualTo(200);
        assertThat(canceled.body()).contains("\"state\":\"CANCELED\"");
        assertThat(client.get("/api/queries/" + queryId, token).body()).contains("\"state\":\"CANCELED\"");
    }

    @Test
    void slowQueryTimesOut()
    {
        fake.setBlocking(true);
        String token = bearer(login("viewer", "viewer"));

        ApiClient.Response start = client.post("/api/queries", token, sql("SELECT slow"));
        ApiClient.Response view = poll(token, extractString(start.body(), "queryId"));
        assertThat(view.body()).contains("\"state\":\"FAILED\"").contains("timed out");
    }

    @Test
    void platformUserIsPassedAsTrinoUser()
    {
        fake.setResult("SELECT 1", new QueryResult(List.of("x"), List.of(List.of(1)), false));
        String token = bearer(login("viewer", "viewer"));
        ApiClient.Response start = client.post("/api/queries", token, sql("SELECT 1"));
        poll(token, extractString(start.body(), "queryId"));
        assertThat(fake.users()).contains("viewer");
    }

    @Test
    void anonymousCannotRunQueries()
    {
        assertThat(client.post("/api/queries", null, sql("SELECT 1")).status()).isEqualTo(401);
    }

    @Test
    void blankSqlIsRejected()
    {
        assertThat(client.post("/api/queries", bearer(login("viewer", "viewer")), sql("   ")).status()).isEqualTo(400);
    }

    private ApiClient.Response poll(String token, String queryId)
    {
        for (int i = 0; i < 60; i++) {
            ApiClient.Response response = client.get("/api/queries/" + queryId, token);
            assertThat(response.status()).isEqualTo(200);
            if (!response.body().contains("\"state\":\"RUNNING\"")) {
                return response;
            }
            sleep(50);
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

    private static String sql(String sql)
    {
        return "{\"sql\":\"" + sql + "\"}";
    }

    private static String extractString(String body, String field)
    {
        String marker = "\"" + field + "\":\"";
        int start = body.indexOf(marker) + marker.length();
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }

    private static void sleep(long millis)
    {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
