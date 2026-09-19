package io.trino.datafabric.query;

public class QueryNotFoundException
        extends RuntimeException
{
    public QueryNotFoundException(String queryId)
    {
        super("Query not found: " + queryId);
    }
}
