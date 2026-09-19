package io.trino.datafabric.query;

public enum QueryState
{
    RUNNING,
    FINISHED,
    FAILED,
    CANCELED
}
