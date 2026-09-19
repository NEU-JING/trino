package io.trino.datafabric.table;

public class TableNotFoundException
        extends RuntimeException
{
    public TableNotFoundException(long id)
    {
        super("Registered table not found: " + id);
    }
}
