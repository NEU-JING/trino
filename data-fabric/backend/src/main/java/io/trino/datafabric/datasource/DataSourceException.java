package io.trino.datafabric.datasource;

public class DataSourceException
        extends RuntimeException
{
    public DataSourceException(String message)
    {
        super(message);
    }
}
