package io.trino.datafabric.query;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class QueryService
{
    private final QueryEngine engine;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private final Map<String, QueryExecution> executions = new ConcurrentHashMap<>();
    private final Map<String, QueryEngine.Handle> handles = new ConcurrentHashMap<>();
    private final int maxRows;
    private final Duration timeout;

    public QueryService(
            QueryEngine engine,
            @Value("${data-fabric.query.max-rows:10000}") int maxRows,
            @Value("${data-fabric.query.timeout:30s}") Duration timeout,
            @Value("${data-fabric.query.threads:4}") int threads)
    {
        this.engine = engine;
        this.maxRows = maxRows;
        this.timeout = timeout;
        this.executor = Executors.newFixedThreadPool(threads, runnable -> daemonThread(runnable, "data-fabric-query"));
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> daemonThread(runnable, "data-fabric-query-timeout"));
    }

    public QueryExecutionView start(String user, String sql)
    {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("sql is required");
        }
        String queryId = UUID.randomUUID().toString();
        QueryExecution execution = new QueryExecution(queryId, user, sql);
        executions.put(queryId, execution);
        executor.submit(() -> run(execution));
        return execution.view();
    }

    private void run(QueryExecution execution)
    {
        ScheduledFuture<?> timeoutTask = null;
        try {
            QueryEngine.Handle handle = engine.start(execution.sql(), execution.owner(), maxRows);
            handles.put(execution.queryId(), handle);
            timeoutTask = scheduler.schedule(
                    () -> {
                        execution.timeout();
                        handle.cancel();
                    },
                    timeout.toMillis(),
                    TimeUnit.MILLISECONDS);
            execution.finish(handle.await());
        }
        catch (Exception e) {
            execution.fail(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        finally {
            if (timeoutTask != null) {
                timeoutTask.cancel(false);
            }
            handles.remove(execution.queryId());
        }
    }

    public QueryExecutionView get(String user, String queryId)
    {
        return requireOwned(user, queryId).view();
    }

    public QueryExecutionView cancel(String user, String queryId)
    {
        QueryExecution execution = requireOwned(user, queryId);
        execution.cancel();
        QueryEngine.Handle handle = handles.get(queryId);
        if (handle != null) {
            handle.cancel();
        }
        return execution.view();
    }

    public long count()
    {
        return executions.size();
    }

    public long countOwnedBy(String owner)
    {
        return executions.values().stream().filter(execution -> execution.owner().equals(owner)).count();
    }

    public List<QueryHistoryView> recent(int limit)
    {
        return executions.values().stream()
                .map(QueryExecution::historyView)
                .sorted(Comparator.comparing(QueryHistoryView::startedAt).reversed())
                .limit(limit)
                .toList();
    }

    public List<QueryHistoryView> recentOwnedBy(String owner, int limit)
    {
        return executions.values().stream()
                .filter(execution -> execution.owner().equals(owner))
                .map(QueryExecution::historyView)
                .sorted(Comparator.comparing(QueryHistoryView::startedAt).reversed())
                .limit(limit)
                .toList();
    }

    private QueryExecution requireOwned(String user, String queryId)
    {
        QueryExecution execution = executions.get(queryId);
        if (execution == null || !execution.owner().equals(user)) {
            throw new QueryNotFoundException(queryId);
        }
        return execution;
    }

    @PreDestroy
    public void shutdown()
    {
        executor.shutdownNow();
        scheduler.shutdownNow();
    }

    private static Thread daemonThread(Runnable runnable, String name)
    {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }
}
