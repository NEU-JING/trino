package io.trino.datafabric.api;

import io.trino.datafabric.auth.RequireRole;
import io.trino.datafabric.dataset.CreateDatasetRequest;
import io.trino.datafabric.dataset.DatasetDetailView;
import io.trino.datafabric.dataset.DatasetService;
import io.trino.datafabric.dataset.DatasetVersionView;
import io.trino.datafabric.dataset.DatasetView;
import io.trino.datafabric.auth.AuthInterceptor;
import io.trino.datafabric.auth.RequireAuthenticated;
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
@RequestMapping("/api/datasets")
public class DatasetController
{
    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService)
    {
        this.datasetService = datasetService;
    }

    @GetMapping
    @RequireAuthenticated
    public List<DatasetView> list(@RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user)
    {
        return user.role() == Role.OPERATOR ? datasetService.list() : datasetService.listConsumable();
    }

    @GetMapping("/{uid}")
    @RequireAuthenticated
    public DatasetDetailView detail(@PathVariable String uid)
    {
        return datasetService.detailByUid(uid);
    }

    @GetMapping("/{uid}/versions")
    @RequireAuthenticated
    public List<DatasetVersionView> versions(@PathVariable String uid)
    {
        return datasetService.versions(uid);
    }

    @PostMapping
    @RequireRole(Role.OPERATOR)
    @ResponseStatus(HttpStatus.CREATED)
    public DatasetDetailView create(
            @RequestBody CreateDatasetRequest request,
            @RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user)
    {
        return datasetService.create(user.username(), request);
    }

    @PutMapping("/{uid}/definition")
    @RequireRole(Role.OPERATOR)
    public DatasetDetailView updateDefinition(@PathVariable String uid, @RequestBody CreateDatasetRequest request)
    {
        return datasetService.updateDefinition(uid, request);
    }

    @PostMapping("/{uid}/publish")
    @RequireRole(Role.OPERATOR)
    public DatasetDetailView publish(
            @PathVariable String uid,
            @RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user)
    {
        return datasetService.publish(uid, user.username());
    }

    @PostMapping("/{uid}/deprecate")
    @RequireRole(Role.OPERATOR)
    public DatasetDetailView deprecate(@PathVariable String uid)
    {
        return datasetService.deprecate(uid);
    }

    @PutMapping("/{uid}/materialization")
    @RequireRole(Role.OPERATOR)
    public DatasetDetailView setMaterialization(@PathVariable String uid, @RequestBody MaterializationModeRequest request)
    {
        return datasetService.setMaterializationMode(uid, request.mode());
    }

    @PostMapping("/{uid}/materialization/refresh")
    @RequireRole(Role.OPERATOR)
    public DatasetDetailView refreshMaterialization(@PathVariable String uid)
    {
        return datasetService.refreshMaterialization(uid);
    }

    @DeleteMapping("/{uid}")
    @RequireRole(Role.OPERATOR)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String uid)
    {
        datasetService.delete(uid);
    }

    public record MaterializationModeRequest(String mode) {}
}
