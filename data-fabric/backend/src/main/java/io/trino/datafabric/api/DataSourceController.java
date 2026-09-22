package io.trino.datafabric.api;

import io.trino.datafabric.auth.AuthInterceptor;
import io.trino.datafabric.auth.RequireAuthenticated;
import io.trino.datafabric.auth.RequireRole;
import io.trino.datafabric.datasource.DataSourceRequest;
import io.trino.datafabric.datasource.DataSourceService;
import io.trino.datafabric.datasource.DataSourceView;
import io.trino.datafabric.security.Role;
import io.trino.datafabric.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/data-sources")
public class DataSourceController
{
    private final DataSourceService dataSourceService;

    public DataSourceController(DataSourceService dataSourceService)
    {
        this.dataSourceService = dataSourceService;
    }

    @GetMapping
    @RequireAuthenticated
    public List<DataSourceView> list()
    {
        return dataSourceService.list();
    }

    @GetMapping("/{id}")
    @RequireAuthenticated
    public DataSourceView get(@PathVariable long id)
    {
        return dataSourceService.get(id);
    }

    @PostMapping
    @RequireRole(Role.OPERATOR)
    @ResponseStatus(HttpStatus.CREATED)
    public DataSourceView create(
            @RequestBody DataSourceRequest request,
            @RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user)
    {
        return dataSourceService.register(user.username(), request);
    }

    @PostMapping("/test")
    @RequireRole(Role.OPERATOR)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void test(@RequestBody DataSourceRequest request)
    {
        dataSourceService.testConnection(request);
    }

    @PutMapping("/{id}")
    @RequireRole(Role.OPERATOR)
    public DataSourceView update(@PathVariable long id, @RequestBody DataSourceRequest request)
    {
        return dataSourceService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @RequireRole(Role.OPERATOR)
    public DataSourceView disable(@PathVariable long id)
    {
        return dataSourceService.disable(id);
    }
}
