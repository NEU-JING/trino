package io.trino.datafabric.accesscontrol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.trino.datafabric.permission.PermissionRepository;
import io.trino.datafabric.permission.PrincipalType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AccessControlRulesGeneratorTest
{
    private static final String QUOTED_VIEWER = "\\Qviewer\\E";
    private static final String QUOTED_ADMIN = "\\Qadmin\\E";
    private static final String QUOTED_SERVICE = "\\Qtrino_service\\E";

    @Autowired
    private AccessControlRulesGenerator generator;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp()
    {
        jdbcTemplate.update("DELETE FROM access_permission");
    }

    @Test
    void generatesServiceOperatorAndTableRules()
            throws Exception
    {
        permissionRepository.grant("viewer", PrincipalType.USER, "ob_demo", "ob_source", "orders", "SELECT");

        JsonNode root = new ObjectMapper().readTree(generator.generate());

        JsonNode serviceCatalog = root.get("catalogs").get(0);
        assertThat(serviceCatalog.get("user").asText()).isEqualTo(QUOTED_SERVICE);
        assertThat(serviceCatalog.get("allow").asText()).isEqualTo("owner");

        JsonNode fallbackCatalog = root.get("catalogs").get(1);
        assertThat(fallbackCatalog.get("allow").asText()).isEqualTo("read-only");
        assertThat(fallbackCatalog.has("user")).isFalse();

        JsonNode adminRule = findTableRule(root, QUOTED_ADMIN);
        assertThat(adminRule).isNotNull();
        assertThat(adminRule.get("privileges").get(0).asText()).isEqualTo("SELECT");
        assertThat(adminRule.get("table").asText()).isEqualTo(".*");

        JsonNode viewerRule = findTableRule(root, QUOTED_VIEWER);
        assertThat(viewerRule).isNotNull();
        assertThat(viewerRule.get("catalog").asText()).isEqualTo("\\Qob_demo\\E");
        assertThat(viewerRule.get("schema").asText()).isEqualTo("\\Qob_source\\E");
        assertThat(viewerRule.get("table").asText()).isEqualTo("\\Qorders\\E");
        assertThat(viewerRule.get("privileges").get(0).asText()).isEqualTo("SELECT");
    }

    @Test
    void allowsSessionProperties()
            throws Exception
    {
        JsonNode root = new ObjectMapper().readTree(generator.generate());
        assertThat(root.get("system_session_properties").get(0).get("allow").asBoolean()).isTrue();
        assertThat(root.get("catalog_session_properties").get(0).get("allow").asBoolean()).isTrue();
    }

    @Test
    void roleGrantExpandsToUsersWithThatRole()
            throws Exception
    {
        permissionRepository.grant("QUERY_USER", PrincipalType.ROLE, "gp_demo", "public", "customers", "SELECT");

        JsonNode root = new ObjectMapper().readTree(generator.generate());
        JsonNode viewerRule = findTableRule(root, QUOTED_VIEWER);
        assertThat(viewerRule).isNotNull();
        assertThat(viewerRule.get("table").asText()).isEqualTo("\\Qcustomers\\E");
    }

    private static JsonNode findTableRule(JsonNode root, String user)
    {
        for (JsonNode rule : root.get("tables")) {
            if (user.equals(rule.get("user").asText())) {
                return rule;
            }
        }
        return null;
    }
}
