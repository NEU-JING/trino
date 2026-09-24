package io.trino.datafabric.dataset;

public class DatasetNotFoundException
        extends RuntimeException
{
    public DatasetNotFoundException(long id)
    {
        super("Dataset not found: " + id);
    }

    public DatasetNotFoundException(String uid)
    {
        super("Dataset not found: " + uid);
    }
}
