package io.trino.datafabric.auth;

import io.trino.datafabric.security.PasswordHasher;
import io.trino.datafabric.user.User;
import io.trino.datafabric.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService
{
    private final UserRepository userRepository;
    private final TokenStore tokenStore;

    public AuthService(UserRepository userRepository, TokenStore tokenStore)
    {
        this.userRepository = userRepository;
        this.tokenStore = tokenStore;
    }

    public Optional<User> authenticate(String username, String password)
    {
        return userRepository.findByUsername(username)
                .filter(User::enabled)
                .filter(user -> PasswordHasher.verify(password, user.passwordHash()));
    }

    public String login(String username, String password)
    {
        User user = authenticate(username, password).orElseThrow(InvalidCredentialsException::new);
        return tokenStore.issue(user.username());
    }

    public Optional<User> resolveToken(String token)
    {
        return tokenStore.resolve(token).flatMap(userRepository::findByUsername);
    }
}
