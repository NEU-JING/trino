package io.trino.datafabric.api;

import io.trino.datafabric.auth.AuthInterceptor;
import io.trino.datafabric.auth.RequireAuthenticated;
import io.trino.datafabric.query.QueryExecutionView;
import io.trino.datafabric.query.QueryService;
import io.trino.datafabric.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/queries")
@RequireAuthenticated
public class QueryController
{
    private final QueryService queryService;

    public QueryController(QueryService queryService)
    {
        this.queryService = queryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public QueryExecutionView start(
            @RequestBody StartQueryRequest request,
            @RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user)
    {
        return queryService.start(user.username(), request.sql());
    }

    @GetMapping("/{id}")
    public QueryExecutionView get(
            @PathVariable String id,
            @RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user)
    {
        return queryService.get(user.username(), id);
    }

    @PostMapping("/{id}/cancel")
    public QueryExecutionView cancel(
            @PathVariable String id,
            @RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user)
    {
        return queryService.cancel(user.username(), id);
    }

    public record StartQueryRequest(String sql) {}
}
