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
@Import(DatasetApiTest.FakeConfig.class)
class DatasetApiTest
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
        jdbcTemplate.update("DELETE FROM dataset_usage");
        jdbcTemplate.update("DELETE FROM dataset_materialization");
        jdbcTemplate.update("DELETE FROM dataset_relation");
        jdbcTemplate.update("DELETE FROM dataset_version");
        jdbcTemplate.update("DELETE FROM dataset_field");
        jdbcTemplate.update("DELETE FROM dataset");
        jdbcTemplate.update("DELETE FROM registered_table");
        jdbcTemplate.update("DELETE FROM access_permission");
        jdbcTemplate.update("DELETE FROM data_source");
    }

    @Test
    void createsPublishesAndListsDataset()
    {
        String token = bearer(operator());
        long tableId = registeredTable("ds_ob", "public", "customers");
        String created = createBaseDataset(token, "customers_ds", tableId, "customer_id");
        assertThat(created).contains("\"status\":\"DRAFT\"").contains("\"kind\":\"BASE\"");
        String uid = extractString(created, "uid");

        ApiClient.Response published = client.post("/api/datasets/" + uid + "/publish", token, null);
        assertThat(published.status()).isEqualTo(200);
        assertThat(published.body()).contains("\"status\":\"PUBLISHED\"").contains("\"currentVersion\":1");

        assertThat(client.get("/api/model/datasets", token).body()).contains("customers_ds");
        assertThat(client.get("/api/datasets/" + uid, token).body()).contains("customer_id");
    }

    @Test
    void viewerCannotManageDatasets()
    {
        String operatorToken = bearer(operator());
        long tableId = registeredTable("ds_ob2", "public", "customers");
        String uid = extractString(createBaseDataset(operatorToken, "guarded_ds", tableId, "customer_id"), "uid");

        assertThat(client.post("/api/datasets", bearer(viewer()),
                baseBody("viewer_ds", tableId, "customer_id")).status()).isEqualTo(403);
        assertThat(client.post("/api/datasets/" + uid + "/publish", bearer(viewer()), null).status()).isEqualTo(403);
        assertThat(client.post("/api/model/relations/infer", bearer(viewer()), null).status()).isEqualTo(403);
    }

    @Test
    void writeBackRequiresOperatorAndAggregateKind()
    {
        String token = bearer(operator());
        long tableId = registeredTable("ds_ob3", "public", "orders");
        String uid = extractString(createBaseDataset(token, "orders_ds", tableId, "order_id"), "uid");

        assertThat(client.post("/api/model/datasets", bearer(viewer()),
                aggregateBody("metric_viewer", uid)).status()).isEqualTo(403);
        assertThat(client.post("/api/model/datasets", token,
                baseBody("metric_wrong_kind", tableId, "order_id")).status()).isEqualTo(400);

        ApiClient.Response written = client.post("/api/model/datasets", token, aggregateBody("metric_sales", uid));
        assertThat(written.status()).isEqualTo(201);
        assertThat(written.body()).contains("\"kind\":\"AGGREGATE\"");
    }

    @Test
    void infersConfirmsAndGraphsRelations()
    {
        String token = bearer(operator());
        long dataSourceId = createDataSource("ds_rel");
        long tableA = registerTable(dataSourceId, "public", "a");
        long tableB = registerTable(dataSourceId, "public", "b");
        createBaseDataset(token, "rel_a", tableA, "customer_id");
        createBaseDataset(token, "rel_b", tableB, "customer_id");

        ApiClient.Response inferred = client.post("/api/model/relations/infer", token, null);
        assertThat(inferred.status()).isEqualTo(200);
        assertThat(inferred.body()).contains("INFERRED");
        long relationId = extractLong(inferred.body(), "id");

        assertThat(client.get("/api/model/relations", token).body()).contains("customer_id");
        assertThat(client.post("/api/model/relations/" + relationId + "/confirm", token, null).body()).contains("DECLARED");
        assertThat(client.get("/api/model/graph", token).body()).contains("rel_a").contains("rel_b");
    }

    @Test
    void lineageShowsUpstreamRegisteredTable()
    {
        String token = bearer(operator());
        long tableId = registeredTable("ds_lin", "public", "orders");
        String uid = extractString(createBaseDataset(token, "lineage_ds", tableId, "order_id"), "uid");

        ApiClient.Response lineage = client.get("/api/model/lineage/" + uid, token);
        assertThat(lineage.status()).isEqualTo(200);
        assertThat(lineage.body()).contains("ds_lin.public.orders").contains("\"type\":\"table\"");
    }

    @Test
    void usageEndpointIsOperatorOnly()
    {
        assertThat(client.get("/api/usage/datasets", bearer(viewer())).status()).isEqualTo(403);
        assertThat(client.get("/api/usage/datasets", bearer(operator())).status()).isEqualTo(200);
    }

    @Test
    void publishingKeepsStableUidAndAccumulatesVersions()
    {
        String token = bearer(operator());
        long tableId = registeredTable("ds_ver", "public", "customers");
        String created = createBaseDataset(token, "versioned_ds", tableId, "customer_id");
        String uid = extractString(created, "uid");

        client.post("/api/datasets/" + uid + "/publish", token, null);
        ApiClient.Response second = client.post("/api/datasets/" + uid + "/publish", token, null);
        assertThat(second.body()).contains("\"currentVersion\":2");

        ApiClient.Response versions = client.get("/api/datasets/" + uid + "/versions", token);
        assertThat(countOccurrences(versions.body(), "\"version\"")).isEqualTo(2);
        assertThat(client.get("/api/datasets/" + uid, token).body()).contains(uid);
    }

    private long registeredTable(String dataSourceName, String schema, String table)
    {
        return registerTable(createDataSource(dataSourceName), schema, table);
    }

    private long createDataSource(String name)
    {
        ApiClient.Response source = client.post("/api/data-sources", bearer(operator()),
                "{\"name\":\"%s\",\"businessType\":\"OceanBase\",\"host\":\"h\",\"port\":3306,\"database\":\"\",\"user\":\"u\",\"password\":\"p\"}"
                        .formatted(name));
        assertThat(source.status()).isEqualTo(201);
        return extractLong(source.body(), "id");
    }

    private long registerTable(long dataSourceId, String schema, String table)
    {
        ApiClient.Response registered = client.post("/api/tables", bearer(operator()),
                "{\"dataSourceId\":%d,\"schema\":\"%s\",\"table\":\"%s\",\"description\":\"d\"}".formatted(dataSourceId, schema, table));
        assertThat(registered.status()).isEqualTo(201);
        return extractLong(registered.body(), "id");
    }

    private String createBaseDataset(String token, String name, long baseTableId, String idField)
    {
        ApiClient.Response response = client.post("/api/datasets", token, baseBody(name, baseTableId, idField));
        assertThat(response.status()).isEqualTo(201);
        return response.body();
    }

    private static String baseBody(String name, long baseTableId, String idField)
    {
        String fields = "[{\"name\":\"%s\",\"role\":\"ID\"},{\"name\":\"name\",\"role\":\"DIMENSION\"}]".formatted(idField);
        return "{\"name\":\"%s\",\"description\":\"d\",\"domain\":\"oa\",\"kind\":\"BASE\",\"baseTableId\":%d,\"fields\":%s}"
                .formatted(name, baseTableId, fields);
    }

    private static String aggregateBody(String name, String inputUid)
    {
        return ("{\"name\":\"%s\",\"description\":\"d\",\"domain\":\"app\",\"kind\":\"AGGREGATE\",\"inputs\":[\"%s\"],"
                + "\"groupBy\":[\"t0.name\"],\"measures\":[{\"name\":\"cnt\",\"expression\":\"count(*)\",\"aggregation\":\"COUNT\"}],"
                + "\"fields\":[{\"name\":\"name\",\"role\":\"DIMENSION\"},{\"name\":\"cnt\",\"role\":\"MEASURE\",\"aggregation\":\"COUNT\"}]}")
                .formatted(name, inputUid);
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
