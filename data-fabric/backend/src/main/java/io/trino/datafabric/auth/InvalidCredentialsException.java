package io.trino.datafabric.auth;

public class InvalidCredentialsException
        extends RuntimeException
{
    public InvalidCredentialsException()
    {
        super("Invalid username or password");
    }
}
