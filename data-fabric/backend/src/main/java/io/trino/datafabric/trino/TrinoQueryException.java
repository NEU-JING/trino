package io.trino.datafabric.trino;

public class TrinoQueryException
        extends RuntimeException
{
    public TrinoQueryException(String message)
    {
        super(message);
    }
}
