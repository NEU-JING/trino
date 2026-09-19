package io.trino.datafabric.accesscontrol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.trino.datafabric.permission.Permission;
import io.trino.datafabric.permission.PermissionRepository;
import io.trino.datafabric.permission.PrincipalType;
import io.trino.datafabric.security.Role;
import io.trino.datafabric.user.User;
import io.trino.datafabric.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Generates a {@code FileBasedSystemAccessControl} rules document from the platform's
 * users and table grants. Served over HTTP and polled by Trino.
 */
@Component
public class AccessControlRulesGenerator
{
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final String serviceUser;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AccessControlRulesGenerator(
            PermissionRepository permissionRepository,
            UserRepository userRepository,
            @Value("${data-fabric.trino.user:trino_service}") String serviceUser)
    {
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.serviceUser = serviceUser;
    }

    public String generate()
    {
        ObjectNode root = objectMapper.createObjectNode();

        // Catalog access: the service user owns catalogs (needed for CREATE/DROP CATALOG);
        // everyone else gets read-only access so they can run SELECTs.
        ArrayNode catalogs = root.putArray("catalogs");
        catalogs.addObject().put("user", Pattern.quote(serviceUser)).put("catalog", ".*").put("allow", "owner");
        catalogs.addObject().put("allow", "read-only");

        // Table SELECT grants.
        ArrayNode tables = root.putArray("tables");
        tables.add(serviceTableRule());
        List<User> users = userRepository.findAll();
        for (User user : users) {
            if (user.role() == Role.OPERATOR && user.enabled()) {
                tables.add(wildcardTableRule(user.username()));
            }
        }
        for (Permission permission : permissionRepository.findAll()) {
            if (permission.principalType() == PrincipalType.USER) {
                tables.add(tableRule(
                        permission.principal(),
                        permission.catalogName(),
                        permission.schemaName(),
                        permission.tableName(),
                        permission.permission()));
            }
            else {
                for (User user : users) {
                    if (user.enabled() && user.role().name().equalsIgnoreCase(permission.principal())) {
                        tables.add(tableRule(
                                user.username(),
                                permission.catalogName(),
                                permission.schemaName(),
                                permission.tableName(),
                                permission.permission()));
                    }
                }
            }
        }

        // Session properties are not part of the access model; allow them.
        root.putArray("system_session_properties").addObject().put("allow", true).put("property", ".*");
        root.putArray("catalog_session_properties").addObject().put("allow", true).put("catalog", ".*").put("property", ".*");

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to generate access control rules", e);
        }
    }

    private ObjectNode wildcardTableRule(String user)
    {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("user", Pattern.quote(user));
        node.put("catalog", ".*");
        node.put("schema", ".*");
        node.put("table", ".*");
        node.putArray("privileges").add("SELECT");
        return node;
    }

    /** The service account performs catalog management and connectivity probes. */
    private ObjectNode serviceTableRule()
    {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("user", Pattern.quote(serviceUser));
        node.put("catalog", ".*");
        node.put("schema", ".*");
        node.put("table", ".*");
        ArrayNode privileges = node.putArray("privileges");
        privileges.add("SELECT");
        privileges.add("INSERT");
        privileges.add("DELETE");
        privileges.add("UPDATE");
        privileges.add("OWNERSHIP");
        return node;
    }

    private ObjectNode tableRule(String user, String catalog, String schema, String table, String privilege)
    {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("user", Pattern.quote(user));
        node.put("catalog", Pattern.quote(catalog));
        node.put("schema", Pattern.quote(schema));
        node.put("table", Pattern.quote(table));
        node.putArray("privileges").add(privilege);
        return node;
    }
}
