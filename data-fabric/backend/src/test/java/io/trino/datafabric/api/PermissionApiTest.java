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
@Import(PermissionApiTest.FakeConfig.class)
class PermissionApiTest
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

    @BeforeEach
    void setUp()
    {
        client = new ApiClient(port);
        ((FakeTrinoGateway) trinoGateway).reset();
        jdbcTemplate.update("DELETE FROM registered_table");
        jdbcTemplate.update("DELETE FROM access_permission");
        jdbcTemplate.update("DELETE FROM data_source");
    }

    @Test
    void operatorCanGrantAndRulesReflectIt()
    {
        String token = bearer(operator());
        ApiClient.Response granted = client.post("/api/permissions", token, grantBody("viewer", "ob_demo", "ob_source", "orders"));
        assertThat(granted.status()).isEqualTo(201);
        assertThat(granted.body()).contains("viewer").contains("orders");

        ApiClient.Response list = client.get("/api/permissions", token);
        assertThat(list.body()).contains("viewer").contains("ob_demo");

        // rules endpoint is public and used by Trino
        ApiClient.Response rules = client.get("/api/access-control/rules", null);
        assertThat(rules.status()).isEqualTo(200);
        assertThat(rules.body()).contains("ob_demo").contains("orders").contains("viewer");
    }

    @Test
    void revokeRemovesGrant()
    {
        String token = bearer(operator());
        client.post("/api/permissions", token, grantBody("viewer", "ob_demo", "ob_source", "orders"));

        ApiClient.Response revoked = client.post("/api/permissions/revoke", token, grantBody("viewer", "ob_demo", "ob_source", "orders"));
        assertThat(revoked.status()).isEqualTo(204);
        assertThat(client.get("/api/permissions", token).body().trim()).isEqualTo("[]");
    }

    @Test
    void queryUserCannotManagePermissions()
    {
        assertThat(client.post("/api/permissions", bearer(viewer()), grantBody("viewer", "ob_demo", "ob_source", "orders")).status()).isEqualTo(403);
    }

    @Test
    void anonymousCannotListPermissions()
    {
        assertThat(client.get("/api/permissions", null).status()).isEqualTo(401);
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

    private static String grantBody(String principal, String catalog, String schema, String table)
    {
        return "{\"principal\":\"%s\",\"principalType\":\"USER\",\"catalog\":\"%s\",\"schema\":\"%s\",\"table\":\"%s\",\"permission\":\"SELECT\"}"
                .formatted(principal, catalog, schema, table);
    }
}
