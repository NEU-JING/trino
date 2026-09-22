package io.trino.datafabric.query;

import io.trino.datafabric.user.User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class SavedQueryService
{
    private static final int MAX_NAME_LENGTH = 256;

    private final SavedQueryRepository repository;

    public SavedQueryService(SavedQueryRepository repository)
    {
        this.repository = repository;
    }

    public List<SavedQueryView> list(User user)
    {
        return repository.findByOwner(user.username()).stream()
                .map(SavedQueryService::toView)
                .toList();
    }

    public SavedQueryView save(User user, String name, String sql)
    {
        String trimmedName = require(name, "name");
        if (trimmedName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("name is too long (max %d characters)".formatted(MAX_NAME_LENGTH));
        }
        String query = require(sql, "sql");
        Optional<SavedQuery> existing = repository.findByOwnerAndName(user.username(), trimmedName);
        long id;
        if (existing.isPresent()) {
            repository.update(existing.get().id(), query, Instant.now());
            id = existing.get().id();
        }
        else {
            id = repository.insert(trimmedName, query, user.username(), Instant.now());
        }
        return repository.findByOwnerAndName(user.username(), trimmedName)
                .map(SavedQueryService::toView)
                .orElseThrow(() -> new SavedQueryNotFoundException(id));
    }

    public void delete(User user, long id)
    {
        if (repository.delete(user.username(), id) == 0) {
            throw new SavedQueryNotFoundException(id);
        }
    }

    private static SavedQueryView toView(SavedQuery savedQuery)
    {
        return new SavedQueryView(
                savedQuery.id(),
                savedQuery.name(),
                savedQuery.sql(),
                savedQuery.createdAt(),
                savedQuery.updatedAt());
    }

    private static String require(String value, String name)
    {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
