package io.trino.datafabric.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import io.trino.datafabric.security.PasswordHasher;
import io.trino.datafabric.security.Role;

import static java.util.Objects.requireNonNull;

/**
 * Seeds the two prototype accounts on first startup (empty user table).
 */
@Component
public class BootstrapUsers
        implements CommandLineRunner
{
    private final UserRepository userRepository;
    private final boolean enabled;
    private final String operatorUsername;
    private final String operatorPassword;
    private final String queryUsername;
    private final String queryPassword;

    public BootstrapUsers(
            UserRepository userRepository,
            @Value("${data-fabric.bootstrap.enabled:true}") boolean enabled,
            @Value("${data-fabric.bootstrap.operator.username:admin}") String operatorUsername,
            @Value("${data-fabric.bootstrap.operator.password:admin}") String operatorPassword,
            @Value("${data-fabric.bootstrap.query.username:viewer}") String queryUsername,
            @Value("${data-fabric.bootstrap.query.password:viewer}") String queryPassword)
    {
        this.userRepository = requireNonNull(userRepository, "userRepository is null");
        this.enabled = enabled;
        this.operatorUsername = requireNonNull(operatorUsername, "operatorUsername is null");
        this.operatorPassword = requireNonNull(operatorPassword, "operatorPassword is null");
        this.queryUsername = requireNonNull(queryUsername, "queryUsername is null");
        this.queryPassword = requireNonNull(queryPassword, "queryPassword is null");
    }

    @Override
    public void run(String... args)
    {
        if (!enabled || userRepository.count() > 0) {
            return;
        }
        userRepository.save(new User(
                operatorUsername, PasswordHasher.hash(operatorPassword), Role.OPERATOR, true));
        userRepository.save(new User(
                queryUsername, PasswordHasher.hash(queryPassword), Role.QUERY_USER, true));
    }
}
