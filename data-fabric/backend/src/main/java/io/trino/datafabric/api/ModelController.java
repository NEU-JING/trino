package io.trino.datafabric.api;

import io.trino.datafabric.auth.AuthInterceptor;
import io.trino.datafabric.auth.RequireAuthenticated;
import io.trino.datafabric.auth.RequireRole;
import io.trino.datafabric.dataset.CreateDatasetRequest;
import io.trino.datafabric.dataset.DatasetDetailView;
import io.trino.datafabric.dataset.DatasetRelationRequest;
import io.trino.datafabric.dataset.DatasetRelationService;
import io.trino.datafabric.dataset.DatasetRelationView;
import io.trino.datafabric.dataset.DatasetService;
import io.trino.datafabric.dataset.DatasetVersionView;
import io.trino.datafabric.dataset.DatasetView;
import io.trino.datafabric.dataset.LineageView;
import io.trino.datafabric.dataset.RelationGraphView;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Serving contract consumed by data application products and a future metrics platform:
 * read the logical model, inspect relations and lineage, and write aggregate datasets back.
 */
@RestController
@RequestMapping("/api/model")
@RequireAuthenticated
public class ModelController
{
    private final DatasetService datasetService;
    private final DatasetRelationService relationService;

    public ModelController(DatasetService datasetService, DatasetRelationService relationService)
    {
        this.datasetService = datasetService;
        this.relationService = relationService;
    }

    @GetMapping("/datasets")
    public List<DatasetView> datasets()
    {
        return datasetService.listConsumable();
    }

    @GetMapping("/datasets/{uid}")
    public DatasetDetailView dataset(@PathVariable String uid)
    {
        return datasetService.detailByUid(uid);
    }

    @GetMapping("/datasets/{uid}/versions")
    public List<DatasetVersionView> versions(@PathVariable String uid)
    {
        return datasetService.versions(uid);
    }

    @GetMapping("/relations")
    public List<DatasetRelationView> relations(@RequestParam(name = "dataset", required = false) String datasetUid)
    {
        return datasetUid == null || datasetUid.isBlank() ? relationService.list() : relationService.forDataset(datasetUid);
    }

    @GetMapping("/graph")
    public RelationGraphView graph()
    {
        return relationService.graph();
    }

    @GetMapping("/lineage/{uid}")
    public LineageView lineage(@PathVariable String uid)
    {
        return relationService.lineage(uid);
    }

    @PostMapping("/datasets")
    @RequireRole(Role.OPERATOR)
    @ResponseStatus(HttpStatus.CREATED)
    public DatasetDetailView writeBack(
            @RequestBody CreateDatasetRequest request,
            @RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user)
    {
        return datasetService.writeBack(user.username(), request);
    }

    @PutMapping("/datasets/{uid}/versions")
    @RequireRole(Role.OPERATOR)
    public DatasetDetailView publish(
            @PathVariable String uid,
            @RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user)
    {
        return datasetService.publish(uid, user.username());
    }

    @PostMapping("/relations")
    @RequireRole(Role.OPERATOR)
    @ResponseStatus(HttpStatus.CREATED)
    public DatasetRelationView declare(
            @RequestBody DatasetRelationRequest request,
            @RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user)
    {
        return relationService.declare(user.username(), request);
    }

    @PostMapping("/relations/infer")
    @RequireRole(Role.OPERATOR)
    public List<DatasetRelationView> infer(@RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user)
    {
        return relationService.infer(user.username());
    }

    @PostMapping("/relations/{id}/confirm")
    @RequireRole(Role.OPERATOR)
    public DatasetRelationView confirm(@PathVariable long id)
    {
        return relationService.confirm(id);
    }

    @DeleteMapping("/relations/{id}")
    @RequireRole(Role.OPERATOR)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRelation(@PathVariable long id)
    {
        relationService.delete(id);
    }
}
