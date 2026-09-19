package io.trino.datafabric.api;

import io.trino.datafabric.auth.AuthInterceptor;
import io.trino.datafabric.auth.AuthService;
import io.trino.datafabric.auth.InvalidCredentialsException;
import io.trino.datafabric.auth.TokenStore;
import io.trino.datafabric.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController
{
    private final AuthService authService;
    private final TokenStore tokenStore;

    public AuthController(AuthService authService, TokenStore tokenStore)
    {
        this.authService = authService;
        this.tokenStore = tokenStore;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request)
    {
        String username = request.username() == null ? "" : request.username();
        String password = request.password() == null ? "" : request.password();
        String token = authService.login(username, password);
        User user = authService.resolveToken(token).orElseThrow(InvalidCredentialsException::new);
        return new LoginResponse(token, user.username(), user.role().name());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization)
    {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            tokenStore.revoke(authorization.substring("Bearer ".length()).trim());
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserView me(@RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user)
    {
        return new UserView(user.username(), user.role().name(), user.enabled());
    }

    public record LoginRequest(String username, String password) {}

    public record LoginResponse(String token, String username, String role) {}

    public record UserView(String username, String role, boolean enabled) {}
}
