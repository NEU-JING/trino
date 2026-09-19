package io.trino.datafabric.auth;

import io.trino.datafabric.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

@Component
public class AuthInterceptor
        implements HandlerInterceptor
{
    public static final String USER_ATTRIBUTE = "dataFabricUser";

    private final AuthService authService;

    public AuthInterceptor(AuthService authService)
    {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException
    {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        User user = resolve(request).orElse(null);
        if (user != null) {
            request.setAttribute(USER_ATTRIBUTE, user);
        }

        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        boolean authenticatedOnly = handlerMethod.hasMethodAnnotation(RequireAuthenticated.class)
                || handlerMethod.getBeanType().isAnnotationPresent(RequireAuthenticated.class);
        if (requireRole == null && !authenticatedOnly) {
            return true;
        }
        if (user == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "authentication required");
            return false;
        }
        if (requireRole != null && user.role() != requireRole.value()) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "insufficient role");
            return false;
        }
        return true;
    }

    private Optional<User> resolve(HttpServletRequest request)
    {
        String header = request.getHeader("Authorization");
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        if (header.startsWith("Bearer ")) {
            return authService.resolveToken(header.substring("Bearer ".length()).trim());
        }
        if (header.startsWith("Basic ")) {
            String decoded;
            try {
                decoded = new String(
                        Base64.getDecoder().decode(header.substring("Basic ".length()).trim()),
                        StandardCharsets.UTF_8);
            }
            catch (IllegalArgumentException e) {
                return Optional.empty();
            }
            int separator = decoded.indexOf(':');
            if (separator < 0) {
                return Optional.empty();
            }
            return authService.authenticate(decoded.substring(0, separator), decoded.substring(separator + 1));
        }
        return Optional.empty();
    }

    private static void writeError(HttpServletResponse response, int status, String message)
            throws IOException
    {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
