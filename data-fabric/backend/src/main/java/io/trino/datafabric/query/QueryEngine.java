package io.trino.datafabric.query;

/**
 * Starts a Trino statement and exposes a cancellable handle. Implemented by
 * {@link StatementClientQueryEngine}; faked in tests.
 */
public interface QueryEngine
{
    Handle start(String sql, String user, int maxRows);

    interface Handle
    {
        QueryResult await()
                throws Exception;

        void cancel();
    }
}
