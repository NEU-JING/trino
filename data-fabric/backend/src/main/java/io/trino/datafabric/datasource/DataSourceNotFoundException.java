package io.trino.datafabric.datasource;

public class DataSourceNotFoundException
        extends RuntimeException
{
    public DataSourceNotFoundException(long id)
    {
        super("Data source not found: " + id);
    }
}
