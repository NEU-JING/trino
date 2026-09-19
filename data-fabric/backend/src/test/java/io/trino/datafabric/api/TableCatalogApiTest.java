package io.trino.datafabric.api;

import io.trino.datafabric.permission.PermissionRepository;
import io.trino.datafabric.permission.PrincipalType;
import io.trino.datafabric.support.ApiClient;
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
@Import(TableCatalogApiTest.FakeConfig.class)
class TableCatalogApiTest
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
    }

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TrinoGateway trinoGateway;

    @Autowired
    private PermissionRepository permissionRepository;

    private ApiClient client;
    private FakeTrinoGateway fake;

    @BeforeEach
    void setUp()
    {
        client = new ApiClient(port);
        fake = (FakeTrinoGateway) trinoGateway;
        fake.reset();
        jdbcTemplate.update("DELETE FROM registered_table");
        jdbcTemplate.update("DELETE FROM access_permission");
        jdbcTemplate.update("DELETE FROM data_source");
    }

    @Test
    void discoversTablesFromDataSource()
    {
        long dataSourceId = createDataSource("tbl_ob");
        fake.setQueryResult(
                List.of("table_schema", "table_name", "table_type"),
                List.of(
                        List.of("public", "customers", "BASE TABLE"),
                        List.of("information_schema", "tables", "BASE TABLE")));

        ApiClient.Response response = client.get("/api/data-sources/" + dataSourceId + "/tables", bearer(operator()));
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("customers");
        assertThat(response.body()).doesNotContain("information_schema");
    }

    @Test
    void registerIsIdempotentAndUpdatesDescription()
    {
        long dataSourceId = createDataSource("tbl_ob");
        String token = bearer(operator());

        assertThat(client.post("/api/tables", token, registerBody(dataSourceId, "public", "customers", "客户表")).status()).isEqualTo(201);
        assertThat(client.post("/api/tables", token, registerBody(dataSourceId, "public", "customers", "客户表 v2")).status()).isEqualTo(201);

        ApiClient.Response list = client.get("/api/tables", token);
        assertThat(countOccurrences(list.body(), "\"table\":\"customers\"")).isEqualTo(1);
        assertThat(list.body()).contains("客户表 v2");
    }

    @Test
    void viewerOnlySeesGrantedTables()
    {
        long dataSourceId = createDataSource("tbl_ob");
        client.post("/api/tables", bearer(operator()), registerBody(dataSourceId, "public", "customers", "客户表"));

        ApiClient.Response before = client.get("/api/tables", bearer(viewer()));
        assertThat(before.status()).isEqualTo(200);
        assertThat(before.body()).doesNotContain("customers");

        permissionRepository.grant("viewer", PrincipalType.USER, "tbl_ob", "public", "customers", "SELECT");

        ApiClient.Response after = client.get("/api/tables", bearer(viewer()));
        assertThat(after.body()).contains("customers");
    }

    @Test
    void operatorSeesAllRegisteredTablesWithAlias()
    {
        long dataSourceId = createDataSource("tbl_ob");
        client.post("/api/tables", bearer(operator()), registerBody(dataSourceId, "public", "customers", "客户表"));

        ApiClient.Response list = client.get("/api/tables", bearer(operator()));
        assertThat(list.body()).contains("customers").contains("OceanBase");
    }

    @Test
    void searchMatchesNameAndDescription()
    {
        long dataSourceId = createDataSource("tbl_ob");
        String token = bearer(operator());
        client.post("/api/tables", token, registerBody(dataSourceId, "public", "customers", "客户表"));
        client.post("/api/tables", token, registerBody(dataSourceId, "public", "orders", "订单表"));

        assertThat(client.get("/api/tables?q=订单", token).body()).contains("orders").doesNotContain("customers");
        assertThat(client.get("/api/tables?q=customers", token).body()).contains("customers");
        assertThat(client.get("/api/tables?q=zzz", token).body().trim()).isEqualTo("[]");
    }

    @Test
    void unregisterRemovesTable()
    {
        long dataSourceId = createDataSource("tbl_ob");
        String token = bearer(operator());
        ApiClient.Response created = client.post("/api/tables", token, registerBody(dataSourceId, "public", "customers", "客户表"));
        long id = extractLong(created.body(), "id");

        assertThat(client.delete("/api/tables/" + id, token).status()).isEqualTo(204);
        assertThat(client.get("/api/tables", token).body()).doesNotContain("customers");
    }

    @Test
    void queryUserCannotRegisterOrDiscover()
    {
        long dataSourceId = createDataSource("tbl_ob");
        assertThat(client.post("/api/tables", bearer(viewer()), registerBody(dataSourceId, "public", "customers", "x")).status()).isEqualTo(403);
        assertThat(client.get("/api/data-sources/" + dataSourceId + "/tables", bearer(viewer())).status()).isEqualTo(403);
    }

    @Test
    void anonymousCannotList()
    {
        assertThat(client.get("/api/tables", null).status()).isEqualTo(401);
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
        String marker = "\"token\":\"";
        int start = response.body().indexOf(marker) + marker.length();
        int end = response.body().indexOf('"', start);
        return response.body().substring(start, end);
    }

    private static String registerBody(long dataSourceId, String schema, String table, String description)
    {
        return "{\"dataSourceId\":%d,\"schema\":\"%s\",\"table\":\"%s\",\"description\":\"%s\"}"
                .formatted(dataSourceId, schema, table, description);
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

    private static int countOccurrences(String haystack, String needle)
    {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
