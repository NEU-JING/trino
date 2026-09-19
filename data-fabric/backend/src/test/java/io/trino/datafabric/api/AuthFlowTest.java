package io.trino.datafabric.api;

import io.trino.datafabric.support.ApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthFlowTest
{
    @Value("${local.server.port}")
    private int port;

    private ApiClient client;

    @BeforeEach
    void setUp()
    {
        client = new ApiClient(port);
    }

    @Test
    void loginWithValidCredentialsReturnsTokenAndRole()
    {
        ApiClient.Response response = client.post("/api/auth/login", null, "{\"username\":\"admin\",\"password\":\"admin\"}");
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("\"token\"");
        assertThat(response.body()).contains("\"role\":\"OPERATOR\"");
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized()
    {
        ApiClient.Response response = client.post("/api/auth/login", null, "{\"username\":\"admin\",\"password\":\"nope\"}");
        assertThat(response.status()).isEqualTo(401);
    }

    @Test
    void anonymousCannotAccessAdminApi()
    {
        assertThat(client.get("/api/admin/users", null).status()).isEqualTo(401);
    }

    @Test
    void queryUserCannotAccessAdminApi()
    {
        String token = login("viewer", "viewer");
        assertThat(client.get("/api/admin/users", "Bearer " + token).status()).isEqualTo(403);
    }

    @Test
    void operatorCanAccessAdminApi()
    {
        String token = login("admin", "admin");
        ApiClient.Response response = client.get("/api/admin/users", "Bearer " + token);
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("admin");
        assertThat(response.body()).contains("viewer");
    }

    @Test
    void meReturnsCurrentUserViaBasicAuth()
    {
        ApiClient.Response response = client.get("/api/auth/me", ApiClient.basic("admin", "admin"));
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("\"username\":\"admin\"");
    }

    @Test
    void logoutRevokesToken()
    {
        String token = login("admin", "admin");
        assertThat(client.post("/api/auth/logout", "Bearer " + token, null).status()).isEqualTo(204);
        assertThat(client.get("/api/admin/users", "Bearer " + token).status()).isEqualTo(401);
    }

    private String login(String username, String password)
    {
        ApiClient.Response response = client.post(
                "/api/auth/login", null, "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        assertThat(response.status()).isEqualTo(200);
        return extractToken(response.body());
    }

    private static String extractToken(String body)
    {
        String marker = "\"token\":\"";
        int start = body.indexOf(marker) + marker.length();
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }
}
