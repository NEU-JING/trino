package io.trino.datafabric.api;

import io.trino.datafabric.auth.AuthInterceptor;
import io.trino.datafabric.auth.RequireAuthenticated;
import io.trino.datafabric.auth.RequireRole;
import io.trino.datafabric.security.Role;
import io.trino.datafabric.table.DiscoveredTable;
import io.trino.datafabric.table.RegisterTableRequest;
import io.trino.datafabric.table.RegisteredTableView;
import io.trino.datafabric.table.TableCatalogService;
import io.trino.datafabric.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TableCatalogController
{
    private final TableCatalogService tableCatalogService;

    public TableCatalogController(TableCatalogService tableCatalogService)
    {
        this.tableCatalogService = tableCatalogService;
    }

    @GetMapping("/data-sources/{id}/tables")
    @RequireRole(Role.OPERATOR)
    public List<DiscoveredTable> discover(@PathVariable long id)
    {
        return tableCatalogService.discover(id);
    }

    @GetMapping("/tables")
    @RequireAuthenticated
    public List<RegisteredTableView> list(
            @RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user,
            @RequestParam(name = "q", required = false) String query)
    {
        return tableCatalogService.listVisible(user, query);
    }

    @PostMapping("/tables")
    @RequireRole(Role.OPERATOR)
    @ResponseStatus(HttpStatus.CREATED)
    public RegisteredTableView register(
            @RequestBody RegisterTableRequest request,
            @RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user)
    {
        return tableCatalogService.register(user.username(), request);
    }

    @DeleteMapping("/tables/{id}")
    @RequireRole(Role.OPERATOR)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@PathVariable long id)
    {
        tableCatalogService.unregister(id);
    }
}
