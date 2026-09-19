package io.trino.datafabric.api;

import io.trino.datafabric.permission.PermissionRepository;
import io.trino.datafabric.permission.PrincipalType;
import io.trino.datafabric.query.QueryEngine;
import io.trino.datafabric.query.QueryResult;
import io.trino.datafabric.support.ApiClient;
import io.trino.datafabric.support.FakeQueryEngine;
import io.trino.datafabric.support.FakeTrinoGateway;
import io.trino.datafabric.trino.TrinoGateway;
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
@Import(OverviewApiTest.FakeConfig.class)
class OverviewApiTest
{
    @TestConfiguration
    static class FakeConfig
    {
        @Bean
        @Primary
        FakeTrinoGateway fakeTrinoGateway()
        {
            return new FakeTrinoGateway();
        }

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
    private TrinoGateway trinoGateway;

    @Autowired
    private QueryEngine queryEngine;

    @Autowired
    private PermissionRepository permissionRepository;

    private ApiClient client;

    @BeforeEach
    void setUp()
    {
        client = new ApiClient(port);
        ((FakeTrinoGateway) trinoGateway).reset();
        ((FakeQueryEngine) queryEngine).reset();
        jdbcTemplate.update("DELETE FROM registered_table");
        jdbcTemplate.update("DELETE FROM access_permission");
        jdbcTemplate.update("DELETE FROM data_source");
    }

    @Test
    void anonymousCannotReadOverview()
    {
        assertThat(client.get("/api/overview/stats", null).status()).isEqualTo(401);
        assertThat(client.get("/api/overview/topology", null).status()).isEqualTo(401);
    }

    @Test
    void viewerCanReadStats()
    {
        assertThat(client.get("/api/overview/stats", bearer(viewer())).status()).isEqualTo(200);
    }

    @Test
    void queryUserOnlySeesOwnScopedOverview()
    {
        long dataSourceId = createDataSource("ov_scope");
        registerTable(dataSourceId, "public", "granted");
        registerTable(dataSourceId, "public", "hidden");
        permissionRepository.grant("viewer", PrincipalType.USER, "ov_scope", "public", "granted", "SELECT");

        String viewerToken = bearer(viewer());
        String adminToken = bearer(operator());
        ((FakeQueryEngine) queryEngine).setResult(
                "SELECT viewer_only_sql", new QueryResult(List.of("x"), List.of(List.of(1)), false));
        ((FakeQueryEngine) queryEngine).setResult(
                "SELECT admin_only_sql", new QueryResult(List.of("x"), List.of(List.of(1)), false));
        startAndWait(viewerToken, "SELECT viewer_only_sql");
        startAndWait(adminToken, "SELECT admin_only_sql");

        ApiClient.Response viewerStats = client.get("/api/overview/stats", viewerToken);
        assertThat(viewerStats.status()).isEqualTo(200);
        assertThat(viewerStats.body()).contains("\"registeredTableCount\":1");
        assertThat(viewerStats.body()).contains("viewer_only_sql");
        assertThat(viewerStats.body()).doesNotContain("admin_only_sql");
        assertThat(viewerStats.body()).contains("\"dataSourceHealth\":[]");
        assertThat(viewerStats.body()).contains("\"userCount\":0");

        ApiClient.Response viewerTopology = client.get("/api/overview/topology", viewerToken);
        assertThat(viewerTopology.body()).contains("table:ov_scope.public.granted");
        assertThat(viewerTopology.body()).doesNotContain("table:ov_scope.public.hidden");
        assertThat(viewerTopology.body()).doesNotContain("user:admin");

        ApiClient.Response adminTopology = client.get("/api/overview/topology", adminToken);
        assertThat(adminTopology.body()).contains("table:ov_scope.public.hidden");
    }

    @Test
    void statsReflectPlatformState()
    {
        long dataSourceId = createDataSource("ov_ob");
        registerTable(dataSourceId, "public", "orders");

        String token = bearer(operator());
        ((FakeQueryEngine) queryEngine).setResult(
                "SELECT unique_ov_sql", new QueryResult(List.of("x"), List.of(List.of(1)), false));
        ApiClient.Response finished = startAndWait(token, "SELECT unique_ov_sql");

        ApiClient.Response stats = client.get("/api/overview/stats", token);
        assertThat(stats.status()).isEqualTo(200);
        assertThat(stats.body()).contains("\"dataSourceCount\":1");
        assertThat(stats.body()).contains("\"enabledDataSourceCount\":1");
        assertThat(stats.body()).contains("\"registeredTableCount\":1");
        assertThat(stats.body()).contains("\"userCount\":");
        assertThat(stats.body()).contains("unique_ov_sql");
        assertThat(stats.body()).contains("\"businessType\":\"OceanBase\"");
        assertThat(finished.body()).contains("\"state\":\"FINISHED\"").contains("\"x\"");
    }

    @Test
    void topologyLinksSourcesTablesAndGrants()
    {
        long dataSourceId = createDataSource("ov_topo");
        registerTable(dataSourceId, "public", "customers");
        permissionRepository.grant("viewer", PrincipalType.USER, "ov_topo", "public", "customers", "SELECT");

        ApiClient.Response topology = client.get("/api/overview/topology", bearer(operator()));
        assertThat(topology.status()).isEqualTo(200);
        assertThat(topology.body()).contains("\"ds:ov_topo\"");
        assertThat(topology.body()).contains("\"table:ov_topo.public.customers\"");
        assertThat(topology.body()).contains("\"user:viewer\"");
        assertThat(topology.body()).contains("\"type\":\"DATA_SOURCE_TABLE\"");
        assertThat(topology.body()).contains("\"type\":\"GRANT\"");
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

    private long createDataSource(String name)
    {
        ApiClient.Response response = client.post(
                "/api/data-sources",
                bearer(operator()),
                "{\"name\":\"%s\",\"businessType\":\"OceanBase\",\"host\":\"h\",\"port\":3306,\"database\":\"\",\"user\":\"u\",\"password\":\"p\"}"
                        .formatted(name));
        assertThat(response.status()).isEqualTo(201);
        return extractLong(response.body(), "id");
    }

    private void registerTable(long dataSourceId, String schema, String table)
    {
        ApiClient.Response response = client.post(
                "/api/tables",
                bearer(operator()),
                "{\"dataSourceId\":%d,\"schema\":\"%s\",\"table\":\"%s\",\"description\":\"\"}".formatted(dataSourceId, schema, table));
        assertThat(response.status()).isEqualTo(201);
    }

    private String operator()
    {
        return login("admin", "admin");
    }

    private String viewer()
    {
        return login("viewer", "viewer");
    }

    private String bearer(String token)
    {
        return "Bearer " + token;
    }

    private String login(String username, String password)
    {
        ApiClient.Response response = client.post(
                "/api/auth/login", null, "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        assertThat(response.status()).isEqualTo(200);
        return extractString(response.body(), "token");
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
