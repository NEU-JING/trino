package io.trino.datafabric.user;

import io.trino.datafabric.security.PasswordHasher;
import io.trino.datafabric.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class UserRepositoryTest
{
    @Autowired
    private UserRepository userRepository;

    @Test
    void bootstrapSeedsOperatorAndQueryUser()
    {
        assertThat(userRepository.findByUsername("admin"))
                .hasValueSatisfying(user -> assertThat(user.role()).isEqualTo(Role.OPERATOR));
        assertThat(userRepository.findByUsername("viewer"))
                .hasValueSatisfying(user -> assertThat(user.role()).isEqualTo(Role.QUERY_USER));
    }

    @Test
    void saveInsertsThenUpdates()
    {
        userRepository.save(new User("temp-user", PasswordHasher.hash("first"), Role.QUERY_USER, true));
        assertThat(userRepository.exists("temp-user")).isTrue();

        userRepository.save(new User("temp-user", PasswordHasher.hash("second"), Role.OPERATOR, false));
        assertThat(userRepository.findByUsername("temp-user"))
                .hasValueSatisfying(user -> {
                    assertThat(user.role()).isEqualTo(Role.OPERATOR);
                    assertThat(user.enabled()).isFalse();
                    assertThat(PasswordHasher.verify("second", user.passwordHash())).isTrue();
                });
    }
}
