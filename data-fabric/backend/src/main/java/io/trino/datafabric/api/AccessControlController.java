package io.trino.datafabric.api;

import io.trino.datafabric.accesscontrol.AccessControlRulesGenerator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoint polled by Trino's file-based access control. Deliberately
 * unauthenticated: Trino fetches it from within the cluster network.
 */
@RestController
@RequestMapping("/api/access-control")
public class AccessControlController
{
    private final AccessControlRulesGenerator rulesGenerator;

    public AccessControlController(AccessControlRulesGenerator rulesGenerator)
    {
        this.rulesGenerator = rulesGenerator;
    }

    @GetMapping(value = "/rules", produces = "application/json")
    public String rules()
    {
        return rulesGenerator.generate();
    }
}
