package io.trino.datafabric.api;

import io.trino.datafabric.auth.AuthInterceptor;
import io.trino.datafabric.auth.RequireAuthenticated;
import io.trino.datafabric.query.QueryHistoryEntry;
import io.trino.datafabric.query.QueryHistoryService;
import io.trino.datafabric.user.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/query-history")
@RequireAuthenticated
public class QueryHistoryController
{
    private final QueryHistoryService queryHistoryService;

    public QueryHistoryController(QueryHistoryService queryHistoryService)
    {
        this.queryHistoryService = queryHistoryService;
    }

    @GetMapping
    public List<QueryHistoryEntry> list(
            @RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user,
            @RequestParam(required = false) Integer limit)
    {
        return queryHistoryService.list(user, limit);
    }
}
