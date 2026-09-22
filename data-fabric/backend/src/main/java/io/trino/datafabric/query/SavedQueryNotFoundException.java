package io.trino.datafabric.query;

public class SavedQueryNotFoundException
        extends RuntimeException
{
    public SavedQueryNotFoundException(long id)
    {
        super("Saved query not found: " + id);
    }
}
