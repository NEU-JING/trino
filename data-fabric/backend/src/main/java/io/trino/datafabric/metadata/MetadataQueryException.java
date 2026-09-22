package io.trino.datafabric.metadata;

/**
 * Raised when metadata cannot be resolved from the underlying data source, for example an
 * unknown catalog, a disabled source, or a sample query that timed out.
 */
public class MetadataQueryException
        extends RuntimeException
{
    public MetadataQueryException(String message)
    {
        super(message);
    }

    public MetadataQueryException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
