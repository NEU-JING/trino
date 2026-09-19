package io.trino.datafabric.export;

public class ResultNotExportableException
        extends RuntimeException
{
    public ResultNotExportableException(String queryId, String state)
    {
        super("Query " + queryId + " is not exportable because it is in state " + state);
    }
}
