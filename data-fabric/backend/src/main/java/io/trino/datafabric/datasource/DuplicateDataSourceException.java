package io.trino.datafabric.datasource;

public class DuplicateDataSourceException
        extends RuntimeException
{
    public DuplicateDataSourceException(String name)
    {
        super("Data source already exists: " + name);
    }
}
