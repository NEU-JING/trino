package io.trino.datafabric.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ApiClient
{
    private final String base;
    private final HttpClient client = HttpClient.newHttpClient();

    public ApiClient(int port)
    {
        this.base = "http://localhost:" + port;
    }

    public Response get(String path, String authorization)
    {
        return send("GET", path, authorization, null);
    }

    public Response post(String path, String authorization, String jsonBody)
    {
        return send("POST", path, authorization, jsonBody);
    }

    public Response put(String path, String authorization, String jsonBody)
    {
        return send("PUT", path, authorization, jsonBody);
    }

    public Response patch(String path, String authorization, String jsonBody)
    {
        return send("PATCH", path, authorization, jsonBody);
    }

    public Response delete(String path, String authorization)
    {
        return send("DELETE", path, authorization, null);
    }

    public RawResponse getRaw(String path, String authorization)
    {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).GET();
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        try {
            HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new RawResponse(response.statusCode(), response.body(), response.headers().firstValue("Content-Type").orElse(""));
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String basic(String username, String password)
    {
        return "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private Response send(String method, String path, String authorization, String body)
    {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path));
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public record Response(int status, String body) {}

    public record RawResponse(int status, byte[] body, String contentType) {}
}
