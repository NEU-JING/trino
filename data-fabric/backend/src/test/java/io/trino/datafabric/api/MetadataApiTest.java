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
@Import(MetadataApiTest.FakeConfig.class)
class MetadataApiTest
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
    void operatorCatalogSuggestionsComeFromDataSources()
    {
        createDataSource("md_ob");

        ApiClient.Response response = client.get("/api/metadata/suggest?type=catalog", bearer(operator()));
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("md_ob");
    }

    @Test
    void operatorSchemaSuggestionsComeFromInformationSchema()
    {
        createDataSource("md_ob");
        fake.setQueryResult(
                "DISTINCT table_schema",
                List.of("table_schema"),
                List.of(List.of("public"), List.of("information_schema")));

        ApiClient.Response response = client.get("/api/metadata/suggest?type=schema&parent=md_ob", bearer(operator()));
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("public").doesNotContain("information_schema");
    }

    @Test
    void operatorTableSuggestionsComeFromInformationSchema()
    {
        createDataSource("md_ob");
        fake.setQueryResult(
                "table_name FROM",
                List.of("table_name"),
                List.of(List.of("customers"), List.of("orders")));

        ApiClient.Response response = client.get("/api/metadata/suggest?type=table&parent=md_ob.public", bearer(operator()));
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("customers").contains("orders");
    }

    @Test
    void queryUserCatalogSuggestionsExcludeUngranted()
    {
        long dataSourceId = createDataSource("md_ob");
        registerTable(dataSourceId, "public", "customers");

        assertThat(client.get("/api/metadata/suggest?type=catalog", bearer(viewer())).body()).doesNotContain("md_ob");

        permissionRepository.grant("viewer", PrincipalType.USER, "md_ob", "public", "customers", "SELECT");

        assertThat(client.get("/api/metadata/suggest?type=catalog", bearer(viewer())).body()).contains("md_ob");
    }

    @Test
    void queryUserTableSuggestionsExcludeUngranted()
    {
        long dataSourceId = createDataSource("md_ob");
        registerTable(dataSourceId, "public", "customers");
        registerTable(dataSourceId, "public", "orders");
        permissionRepository.grant("viewer", PrincipalType.USER, "md_ob", "public", "customers", "SELECT");

        ApiClient.Response response = client.get("/api/metadata/suggest?type=table&parent=md_ob.public", bearer(viewer()));
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("customers").doesNotContain("orders");
    }

    @Test
    void columnsEndpointReturnsStructure()
    {
        long dataSourceId = createDataSource("md_ob");
        long tableId = registerTable(dataSourceId, "public", "customers");
        fake.setQueryResult(
                "information_schema.columns",
                List.of("column_name", "data_type", "is_nullable", "comment"),
                List.of(List.of("id", "integer", "NO", "主键"), List.of("name", "varchar", "YES", "")));

        ApiClient.Response response = client.get("/api/tables/" + tableId + "/columns", bearer(operator()));
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("\"name\":\"id\"").contains("integer").contains("主键");
    }

    @Test
    void sampleEndpointReturnsRows()
    {
        long dataSourceId = createDataSource("md_ob");
        long tableId = registerTable(dataSourceId, "public", "customers");
        fake.setQueryResult("SELECT * FROM", List.of("id", "name"), List.of(List.of(1, "alice")));

        ApiClient.Response response = client.get("/api/tables/" + tableId + "/sample", bearer(operator()));
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("alice");
    }

    @Test
    void detailEndpointReturnsRowCountAndPrincipals()
    {
        long dataSourceId = createDataSource("md_ob");
        long tableId = registerTable(dataSourceId, "public", "customers");
        fake.setQueryResult("COUNT(*)", List.of("_col0"), List.of(List.of(42L)));
        permissionRepository.grant("viewer", PrincipalType.USER, "md_ob", "public", "customers", "SELECT");

        ApiClient.Response response = client.get("/api/tables/" + tableId + "/detail", bearer(operator()));
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("\"rowCount\":42").contains("viewer");
    }

    @Test
    void viewerCannotReadUngrantedTableMetadata()
    {
        long dataSourceId = createDataSource("md_ob");
        long tableId = registerTable(dataSourceId, "public", "customers");

        assertThat(client.get("/api/tables/" + tableId + "/columns", bearer(viewer())).status()).isEqualTo(404);
        assertThat(client.get("/api/tables/" + tableId + "/sample", bearer(viewer())).status()).isEqualTo(404);
        assertThat(client.get("/api/tables/" + tableId + "/detail", bearer(viewer())).status()).isEqualTo(404);
    }

    @Test
    void connectionTestSucceedsAndReportsFailure()
    {
        String token = bearer(operator());
        assertThat(client.post("/api/data-sources/test", token, requestBody("OceanBase", "h")).status()).isEqualTo(204);

        ApiClient.Response failed = client.post("/api/data-sources/test", token, requestBody("OceanBase", "unreachable"));
        assertThat(failed.status()).isEqualTo(400);
        assertThat(failed.body().toLowerCase()).contains("connection");
    }

    @Test
    void queryUserCannotTestConnection()
    {
        assertThat(client.post("/api/data-sources/test", bearer(viewer()), requestBody("OceanBase", "h")).status()).isEqualTo(403);
    }

    @Test
    void adminCanUpdateUserRoleAndEnabled()
    {
        String token = bearer(operator());
        client.post("/api/admin/users", token, "{\"username\":\"md_user\",\"password\":\"pwd\",\"role\":\"QUERY_USER\"}");

        ApiClient.Response disabled = client.patch("/api/admin/users/md_user", token, "{\"enabled\":false}");
        assertThat(disabled.status()).isEqualTo(200);
        assertThat(disabled.body()).contains("\"enabled\":false");
        assertThat(client.post("/api/auth/login", null, "{\"username\":\"md_user\",\"password\":\"pwd\"}").status()).isEqualTo(401);

        ApiClient.Response promoted = client.patch("/api/admin/users/md_user", token, "{\"enabled\":true,\"role\":\"OPERATOR\"}");
        assertThat(promoted.status()).isEqualTo(200);
        assertThat(promoted.body()).contains("OPERATOR").contains("\"enabled\":true");
    }

    @Test
    void anonymousCannotSuggest()
    {
        assertThat(client.get("/api/metadata/suggest?type=catalog", null).status()).isEqualTo(401);
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

    private long registerTable(long dataSourceId, String schema, String table)
    {
        ApiClient.Response response = client.post(
                "/api/tables",
                bearer(operator()),
                "{\"dataSourceId\":%d,\"schema\":\"%s\",\"table\":\"%s\",\"description\":\"\"}"
                        .formatted(dataSourceId, schema, table));
        assertThat(response.status()).isEqualTo(201);
        return extractLong(response.body(), "id");
    }

    private static String requestBody(String businessType, String host)
    {
        return "{\"name\":\"probe\",\"businessType\":\"%s\",\"host\":\"%s\",\"port\":3306,\"database\":\"demo\",\"user\":\"u\",\"password\":\"p\"}"
                .formatted(businessType, host);
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
}
