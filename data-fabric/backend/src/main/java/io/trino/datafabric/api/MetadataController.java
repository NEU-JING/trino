package io.trino.datafabric.api;

import io.trino.datafabric.auth.AuthInterceptor;
import io.trino.datafabric.auth.RequireAuthenticated;
import io.trino.datafabric.metadata.ColumnMetadataView;
import io.trino.datafabric.metadata.MetadataService;
import io.trino.datafabric.metadata.SampleRowsView;
import io.trino.datafabric.metadata.SuggestionView;
import io.trino.datafabric.metadata.TableDetailView;
import io.trino.datafabric.user.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequireAuthenticated
public class MetadataController
{
    private final MetadataService metadataService;

    public MetadataController(MetadataService metadataService)
    {
        this.metadataService = metadataService;
    }

    @GetMapping("/metadata/suggest")
    public List<SuggestionView> suggest(
            @RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user,
            @RequestParam String type,
            @RequestParam(required = false) String parent,
            @RequestParam(name = "q", required = false) String query)
    {
        return metadataService.suggest(user, type, parent, query);
    }

    @GetMapping("/tables/{id}/columns")
    public List<ColumnMetadataView> columns(
            @RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user,
            @PathVariable long id)
    {
        return metadataService.columns(user, id);
    }

    @GetMapping("/tables/{id}/sample")
    public SampleRowsView sample(
            @RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user,
            @PathVariable long id,
            @RequestParam(required = false) Integer limit)
    {
        return metadataService.sample(user, id, limit);
    }

    @GetMapping("/tables/{id}/detail")
    public TableDetailView detail(
            @RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user,
            @PathVariable long id)
    {
        return metadataService.detail(user, id);
    }
}
