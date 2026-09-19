package io.trino.datafabric.support;

import io.trino.datafabric.query.QueryEngine;
import io.trino.datafabric.query.QueryResult;
import io.trino.datafabric.trino.TrinoQueryException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hand-written fake query engine. Results/failures are keyed by SQL text; when
 * {@code blocking} is set, {@code await()} blocks until the handle is canceled.
 */
public class FakeQueryEngine
        implements QueryEngine
{
    private final Map<String, QueryResult> results = new ConcurrentHashMap<>();
    private final Map<String, String> failures = new ConcurrentHashMap<>();
    private final List<String> users = new CopyOnWriteArrayList<>();
    private volatile boolean blocking;

    public void setResult(String sql, QueryResult result)
    {
        results.put(sql, result);
    }

    public void setFailure(String sql, String message)
    {
        failures.put(sql, message);
    }

    public void setBlocking(boolean blocking)
    {
        this.blocking = blocking;
    }

    public List<String> users()
    {
        return users;
    }

    public void reset()
    {
        results.clear();
        failures.clear();
        users.clear();
        blocking = false;
    }

    @Override
    public Handle start(String sql, String user, int maxRows)
    {
        users.add(user);
        return new Handle()
        {
            private volatile boolean cancelRequested;

            @Override
            public QueryResult await()
                    throws Exception
            {
                if (blocking) {
                    while (!cancelRequested) {
                        Thread.sleep(10);
                    }
                    throw new TrinoQueryException("Query canceled");
                }
                if (failures.containsKey(sql)) {
                    throw new TrinoQueryException(failures.get(sql));
                }
                return results.getOrDefault(sql, new QueryResult(List.of(), List.of(), false));
            }

            @Override
            public void cancel()
            {
                cancelRequested = true;
            }
        };
    }
}
