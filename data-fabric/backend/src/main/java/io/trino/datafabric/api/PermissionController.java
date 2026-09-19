package io.trino.datafabric.api;

import io.trino.datafabric.auth.RequireRole;
import io.trino.datafabric.permission.GrantRequest;
import io.trino.datafabric.permission.PermissionService;
import io.trino.datafabric.permission.PermissionView;
import io.trino.datafabric.security.Role;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/permissions")
@RequireRole(Role.OPERATOR)
public class PermissionController
{
    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService)
    {
        this.permissionService = permissionService;
    }

    @GetMapping
    public List<PermissionView> list()
    {
        return permissionService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<PermissionView> grant(@RequestBody GrantRequest request)
    {
        permissionService.grant(request);
        return permissionService.list();
    }

    @PostMapping("/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@RequestBody GrantRequest request)
    {
        permissionService.revoke(request);
    }
}
