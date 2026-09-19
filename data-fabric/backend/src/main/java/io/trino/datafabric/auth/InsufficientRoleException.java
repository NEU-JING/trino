package io.trino.datafabric.auth;

public class InsufficientRoleException
        extends RuntimeException
{
    public InsufficientRoleException(String message)
    {
        super(message);
    }
}
