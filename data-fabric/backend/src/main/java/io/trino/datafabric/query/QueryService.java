package io.trino.datafabric.query;

import io.trino.datafabric.dataset.DatasetService;
import io.trino.datafabric.dataset.DatasetUsageService;
import io.trino.datafabric.dataset.DatasetView;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
    private final QueryHistoryRepository historyRepository;
    private final DatasetService datasetService;
    private final DatasetUsageService usageService;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private final Map<String, QueryExecution> executions = new ConcurrentHashMap<>();
    private final Map<String, QueryEngine.Handle> handles = new ConcurrentHashMap<>();
    private final int maxRows;
    private final Duration timeout;
    private final int historyLimit;

    public QueryService(
            QueryEngine engine,
            QueryHistoryRepository historyRepository,
            DatasetService datasetService,
            DatasetUsageService usageService,
            @Value("${data-fabric.query.max-rows:10000}") int maxRows,
            @Value("${data-fabric.query.timeout:30s}") Duration timeout,
            @Value("${data-fabric.query.threads:4}") int threads,
            @Value("${data-fabric.query.history-limit:200}") int historyLimit)
    {
        this.engine = engine;
        this.historyRepository = historyRepository;
        this.datasetService = datasetService;
        this.usageService = usageService;
        this.maxRows = maxRows;
        this.timeout = timeout;
        this.historyLimit = historyLimit;
        this.executor = Executors.newFixedThreadPool(threads, runnable -> daemonThread(runnable, "data-fabric-query"));
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> daemonThread(runnable, "data-fabric-query-timeout"));
    }

    public QueryExecutionView start(String user, String sql)
    {
        return start(user, sql, "workbench");
    }

    public QueryExecutionView start(String user, String sql, String application)
    {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("sql is required");
        }
        String queryId = UUID.randomUUID().toString();
        QueryExecution execution = new QueryExecution(queryId, user, sql, application);
        executions.put(queryId, execution);
        historyRepository.started(queryId, user, sql, execution.startedAt());
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
            recordCompletion(execution);
        }
    }

    private void recordCompletion(QueryExecution execution)
    {
        QueryExecutionView view = execution.view();
        Long rowCount = view.state().equals(QueryState.FINISHED.name()) ? (long) view.rows().size() : null;
        historyRepository.completed(
                view.queryId(),
                QueryState.valueOf(view.state()),
                view.finishedAt(),
                rowCount,
                view.truncated(),
                view.error());
        historyRepository.trim(execution.owner(), historyLimit);
        recordDatasetUsage(execution);
    }

    private void recordDatasetUsage(QueryExecution execution)
    {
        QueryExecutionView view = execution.view();
        boolean failed = !QueryState.FINISHED.name().equals(view.state());
        Instant end = view.finishedAt() == null ? Instant.now() : view.finishedAt();
        long latencyMs = Duration.between(view.startedAt(), end).toMillis();
        String sql = execution.sql() == null ? "" : execution.sql().toLowerCase(Locale.ROOT);
        for (DatasetView dataset : datasetService.listConsumable()) {
            if (sql.contains(dataset.name().toLowerCase(Locale.ROOT))) {
                usageService.record(dataset.uid(), execution.application(), execution.owner(), latencyMs, failed);
            }
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
