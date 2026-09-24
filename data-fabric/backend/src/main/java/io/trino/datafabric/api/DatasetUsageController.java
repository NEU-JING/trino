package io.trino.datafabric.api;

import io.trino.datafabric.auth.RequireRole;
import io.trino.datafabric.dataset.DatasetUsageService;
import io.trino.datafabric.dataset.DatasetUsageView;
import io.trino.datafabric.security.Role;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/usage")
@RequireRole(Role.OPERATOR)
public class DatasetUsageController
{
    private final DatasetUsageService usageService;

    public DatasetUsageController(DatasetUsageService usageService)
    {
        this.usageService = usageService;
    }

    @GetMapping("/datasets")
    public List<DatasetUsageView> all()
    {
        return usageService.all();
    }

    @GetMapping("/datasets/{uid}")
    public List<DatasetUsageView> forDataset(@PathVariable String uid)
    {
        return usageService.forDataset(uid);
    }
}
