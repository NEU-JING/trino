package io.trino.datafabric.api;

import io.trino.datafabric.auth.DuplicateUserException;
import io.trino.datafabric.auth.RequireRole;
import io.trino.datafabric.security.PasswordHasher;
import io.trino.datafabric.security.Role;
import io.trino.datafabric.user.User;
import io.trino.datafabric.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/admin/users")
@RequireRole(Role.OPERATOR)
public class UserAdminController
{
    private final UserRepository userRepository;

    public UserAdminController(UserRepository userRepository)
    {
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<UserView> list()
    {
        return userRepository.findAll().stream()
                .map(user -> new UserView(user.username(), user.role().name(), user.enabled()))
                .toList();
    }

    @PostMapping
    public ResponseEntity<UserView> create(@RequestBody CreateUserRequest request)
    {
        if (request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().isBlank()
                || request.role() == null || request.role().isBlank()) {
            throw new IllegalArgumentException("username, password and role are required");
        }
        Role role;
        try {
            role = Role.valueOf(request.role().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown role: " + request.role());
        }
        if (userRepository.exists(request.username())) {
            throw new DuplicateUserException(request.username());
        }
        userRepository.save(new User(request.username(), PasswordHasher.hash(request.password()), role, true));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UserView(request.username(), role.name(), true));
    }

    public record CreateUserRequest(String username, String password, String role) {}

    public record UserView(String username, String role, boolean enabled) {}
}
