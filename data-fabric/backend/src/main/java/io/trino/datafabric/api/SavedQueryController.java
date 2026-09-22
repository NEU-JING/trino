package io.trino.datafabric.api;

import io.trino.datafabric.auth.AuthInterceptor;
import io.trino.datafabric.auth.RequireAuthenticated;
import io.trino.datafabric.query.SavedQueryService;
import io.trino.datafabric.query.SavedQueryView;
import io.trino.datafabric.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/saved-queries")
@RequireAuthenticated
public class SavedQueryController
{
    private final SavedQueryService savedQueryService;

    public SavedQueryController(SavedQueryService savedQueryService)
    {
        this.savedQueryService = savedQueryService;
    }

    @GetMapping
    public List<SavedQueryView> list(@RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user)
    {
        return savedQueryService.list(user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedQueryView save(
            @RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user,
            @RequestBody SaveQueryRequest request)
    {
        return savedQueryService.save(user, request.name(), request.sql());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user,
            @PathVariable long id)
    {
        savedQueryService.delete(user, id);
    }

    public record SaveQueryRequest(String name, String sql) {}
}
