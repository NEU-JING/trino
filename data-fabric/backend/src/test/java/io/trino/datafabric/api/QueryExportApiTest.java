package io.trino.datafabric.api;

import io.trino.datafabric.query.QueryEngine;
import io.trino.datafabric.query.QueryResult;
import io.trino.datafabric.support.ApiClient;
import io.trino.datafabric.support.FakeQueryEngine;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(QueryExportApiTest.FakeConfig.class)
class QueryExportApiTest
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
    void csvExportHasBomHeaderAndRows()
    {
        fake.setResult("SELECT name", new QueryResult(List.of("name"), List.of(List.of("alice"), List.of("bob")), false));
        String token = bearer(login("viewer", "viewer"));
        String queryId = start(token, "SELECT name");
        poll(token, queryId);

        ApiClient.RawResponse raw = client.getRaw("/api/queries/" + queryId + "/export?format=csv", token);
        assertThat(raw.status()).isEqualTo(200);
        assertThat(raw.contentType()).contains("text/csv");
        String body = new String(raw.body(), StandardCharsets.UTF_8);
        assertThat(body).startsWith("\uFEFF").contains("name").contains("alice").contains("bob");
    }

    @Test
    void xlsxExportIsReadable()
            throws Exception
    {
        fake.setResult("SELECT name", new QueryResult(List.of("name"), List.of(List.of("alice")), false));
        String token = bearer(login("viewer", "viewer"));
        String queryId = start(token, "SELECT name");
        poll(token, queryId);

        ApiClient.RawResponse raw = client.getRaw("/api/queries/" + queryId + "/export?format=xlsx", token);
        assertThat(raw.status()).isEqualTo(200);
        assertThat(raw.contentType()).contains("spreadsheetml");
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(raw.body()))) {
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("name");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo("alice");
        }
    }

    @Test
    void failedQueryCannotBeExported()
    {
        fake.setFailure("bad", "boom");
        String token = bearer(login("viewer", "viewer"));
        String queryId = start(token, "bad");
        poll(token, queryId);

        assertThat(client.getRaw("/api/queries/" + queryId + "/export?format=csv", token).status()).isEqualTo(409);
    }

    @Test
    void cannotExportAnotherUsersQuery()
    {
        fake.setResult("SELECT 1", new QueryResult(List.of("x"), List.of(List.of(1)), false));
        String operatorToken = bearer(login("admin", "admin"));
        String queryId = start(operatorToken, "SELECT 1");
        poll(operatorToken, queryId);

        String viewerToken = bearer(login("viewer", "viewer"));
        assertThat(client.getRaw("/api/queries/" + queryId + "/export?format=csv", viewerToken).status()).isEqualTo(404);
    }

    @Test
    void anonymousCannotExport()
    {
        assertThat(client.getRaw("/api/queries/whatever/export?format=csv", null).status()).isEqualTo(401);
    }

    private String start(String token, String sql)
    {
        ApiClient.Response response = client.post("/api/queries", token, "{\"sql\":\"" + sql + "\"}");
        assertThat(response.status()).isEqualTo(202);
        return extractString(response.body(), "queryId");
    }

    private void poll(String token, String queryId)
    {
        for (int i = 0; i < 60; i++) {
            ApiClient.Response response = client.get("/api/queries/" + queryId, token);
            assertThat(response.status()).isEqualTo(200);
            if (!response.body().contains("\"state\":\"RUNNING\"")) {
                return;
            }
            try {
                Thread.sleep(30);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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
}
