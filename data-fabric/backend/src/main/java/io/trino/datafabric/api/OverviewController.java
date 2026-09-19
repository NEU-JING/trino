package io.trino.datafabric.api;

import io.trino.datafabric.auth.AuthInterceptor;
import io.trino.datafabric.auth.RequireAuthenticated;
import io.trino.datafabric.stats.OverviewService;
import io.trino.datafabric.stats.OverviewStatsView;
import io.trino.datafabric.stats.TopologyView;
import io.trino.datafabric.user.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/overview")
@RequireAuthenticated
public class OverviewController
{
    private final OverviewService overviewService;

    public OverviewController(OverviewService overviewService)
    {
        this.overviewService = overviewService;
    }

    @GetMapping("/stats")
    public OverviewStatsView stats(@RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user)
    {
        return overviewService.stats(user);
    }

    @GetMapping("/topology")
    public TopologyView topology(@RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user)
    {
        return overviewService.topology(user);
    }
}
