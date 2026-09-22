package io.trino.datafabric.query;

import io.trino.datafabric.user.User;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QueryHistoryService
{
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final QueryHistoryRepository repository;

    public QueryHistoryService(QueryHistoryRepository repository)
    {
        this.repository = repository;
    }

    public List<QueryHistoryEntry> list(User user, Integer limit)
    {
        int effective = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
        return repository.findByUser(user.username(), effective);
    }
}
