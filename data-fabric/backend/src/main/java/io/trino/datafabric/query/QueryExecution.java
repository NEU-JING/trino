package io.trino.datafabric.query;

import java.time.Instant;
import java.util.List;

/**
 * Mutable state of a query execution. All transitions happen from RUNNING and are synchronized.
 */
public class QueryExecution
{
    private final String queryId;
    private final String owner;
    private final String sql;
    private final Instant startedAt = Instant.now();

    private QueryState state = QueryState.RUNNING;
    private List<String> columns = List.of();
    private List<List<Object>> rows = List.of();
    private boolean truncated;
    private String error;
    private Instant finishedAt;

    public QueryExecution(String queryId, String owner, String sql)
    {
        this.queryId = queryId;
        this.owner = owner;
        this.sql = sql;
    }

    public String queryId()
    {
        return queryId;
    }

    public String owner()
    {
        return owner;
    }

    public String sql()
    {
        return sql;
    }

    public Instant startedAt()
    {
        return startedAt;
    }

    public synchronized void finish(QueryResult result)
    {
        if (state != QueryState.RUNNING) {
            return;
        }
        this.columns = result.columns();
        this.rows = result.rows();
        this.truncated = result.truncated();
        this.state = QueryState.FINISHED;
        this.finishedAt = Instant.now();
    }

    public synchronized void fail(String message)
    {
        if (state != QueryState.RUNNING) {
            return;
        }
        this.error = message;
        this.state = QueryState.FAILED;
        this.finishedAt = Instant.now();
    }

    public synchronized void cancel()
    {
        if (state != QueryState.RUNNING) {
            return;
        }
        this.state = QueryState.CANCELED;
        this.finishedAt = Instant.now();
    }

    public synchronized void timeout()
    {
        if (state != QueryState.RUNNING) {
            return;
        }
        this.error = "Query timed out";
        this.state = QueryState.FAILED;
        this.finishedAt = Instant.now();
    }

    public synchronized QueryExecutionView view()
    {
        return new QueryExecutionView(
                queryId, state.name(), columns, rows, truncated, error, startedAt, finishedAt);
    }

    public synchronized QueryHistoryView historyView()
    {
        return new QueryHistoryView(queryId, owner, sql, state.name(), startedAt, finishedAt);
    }
}
