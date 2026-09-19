package io.trino.datafabric.auth;

public class DuplicateUserException
        extends RuntimeException
{
    public DuplicateUserException(String username)
    {
        super("User already exists: " + username);
    }
}
