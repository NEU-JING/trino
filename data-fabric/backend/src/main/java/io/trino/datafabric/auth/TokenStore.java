package io.trino.datafabric.auth;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory opaque token store. Tokens are lost on restart (acceptable for the prototype).
 */
@Component
public class TokenStore
{
    private final Map<String, String> tokens = new ConcurrentHashMap<>();

    public String issue(String username)
    {
        String token = UUID.randomUUID().toString();
        tokens.put(token, username);
        return token;
    }

    public Optional<String> resolve(String token)
    {
        return Optional.ofNullable(tokens.get(token));
    }

    public void revoke(String token)
    {
        tokens.remove(token);
    }
}
