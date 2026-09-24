package io.trino.datafabric.api;

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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(DataSourceApiTest.FakeConfig.class)
class DataSourceApiTest
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

    private ApiClient client;
    private FakeTrinoGateway fake;

    @BeforeEach
    void setUp()
    {
        client = new ApiClient(port);
        fake = (FakeTrinoGateway) trinoGateway;
        fake.reset();
        jdbcTemplate.update("DELETE FROM data_source");
    }

    @Test
    void registerOceanBaseShowsBusinessAliasAndHidesConnector()
    {
        ApiClient.Response response = client.post("/api/data-sources", bearer(operator()), body("ob_demo", "OceanBase", "db-host"));
        assertThat(response.status()).isEqualTo(201);
        assertThat(response.body()).contains("OceanBase");
        assertThat(response.body()).doesNotContain("mysql");
    }

    @Test
    void registerGreenplumShowsBusinessAliasAndHidesConnector()
    {
        ApiClient.Response response = client.post("/api/data-sources", bearer(operator()), body("gp_demo", "Greenplum", "db-host"));
        assertThat(response.status()).isEqualTo(201);
        assertThat(response.body()).contains("Greenplum");
        assertThat(response.body()).doesNotContain("postgresql");
    }

    @Test
    void registerDamengUsesDamengConnectorAndAlias()
    {
        ApiClient.Response response = client.post("/api/data-sources", bearer(operator()), body("dm_demo", "达梦", "dm-host"));
        assertThat(response.status()).isEqualTo(201);
        assertThat(response.body()).contains("达梦");
        assertThat(response.body()).doesNotContain("dameng");
        assertThat(fake.catalogConnector("dm_demo")).isEqualTo("dameng");
        assertThat(fake.connectionUrl("dm_demo")).isEqualTo("jdbc:dm://dm-host:3306");
    }

    @Test
    void registerUnreachableConnectionIsRejectedAndCatalogRolledBack()
    {
        ApiClient.Response response = client.post("/api/data-sources", bearer(operator()), body("bad_demo", "OceanBase", "unreachable"));
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().toLowerCase()).contains("connection");
        assertThat(fake.hasCatalog("bad_demo")).isFalse();
    }

    @Test
    void duplicateNameIsRejected()
    {
        String token = bearer(operator());
        assertThat(client.post("/api/data-sources", token, body("dup_demo", "OceanBase", "h")).status()).isEqualTo(201);
        assertThat(client.post("/api/data-sources", token, body("dup_demo", "OceanBase", "h")).status()).isEqualTo(409);
    }

    @Test
    void invalidNameIsRejected()
    {
        assertThat(client.post("/api/data-sources", bearer(operator()), body("Bad Name", "OceanBase", "h")).status()).isEqualTo(400);
    }

    @Test
    void queryUserCannotRegister()
    {
        assertThat(client.post("/api/data-sources", bearer(viewer()), body("no_demo", "OceanBase", "h")).status()).isEqualTo(403);
    }

    @Test
    void anonymousCannotList()
    {
        assertThat(client.get("/api/data-sources", null).status()).isEqualTo(401);
    }

    @Test
    void operatorCanListWithAliasOnly()
    {
        String token = bearer(operator());
        client.post("/api/data-sources", token, body("list_demo", "OceanBase", "h"));
        ApiClient.Response response = client.get("/api/data-sources", token);
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("list_demo").contains("OceanBase").doesNotContain("mysql");
    }

    @Test
    void updateReplacesConnection()
    {
        String token = bearer(operator());
        ApiClient.Response created = client.post("/api/data-sources", token, body("upd_demo", "OceanBase", "h1"));
        assertThat(created.status()).isEqualTo(201);
        long id = extractLong(created.body(), "id");

        ApiClient.Response updated = client.put("/api/data-sources/" + id, token, body("upd_demo", "Greenplum", "h2"));
        assertThat(updated.status()).isEqualTo(200);
        assertThat(updated.body()).contains("Greenplum").contains("h2");
        assertThat(fake.hasCatalog("upd_demo")).isTrue();
    }

    @Test
    void disableDropsCatalogAndMarksDisabled()
    {
        String token = bearer(operator());
        ApiClient.Response created = client.post("/api/data-sources", token, body("dis_demo", "OceanBase", "h"));
        long id = extractLong(created.body(), "id");

        ApiClient.Response disabled = client.delete("/api/data-sources/" + id, token);
        assertThat(disabled.status()).isEqualTo(200);
        assertThat(disabled.body()).contains("\"enabled\":false");
        assertThat(fake.hasCatalog("dis_demo")).isFalse();
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

    private static String body(String name, String businessType, String host)
    {
        return "{\"name\":\"%s\",\"businessType\":\"%s\",\"host\":\"%s\",\"port\":3306,\"database\":\"demo_db\",\"user\":\"u\",\"password\":\"p\"}"
                .formatted(name, businessType, host);
    }

    private static long extractLong(String body, String field)
    {
        String marker = "\"" + field + "\":";
        int start = body.indexOf(marker) + marker.length();
        int end = start;
        while (end < body.length() && (Character.isDigit(body.charAt(end)))) {
            end++;
        }
        return Long.parseLong(body.substring(start, end));
    }
}
