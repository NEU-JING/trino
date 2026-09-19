package io.trino.datafabric.user;

import io.trino.datafabric.security.Role;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository
{
    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate)
    {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<User> findByUsername(String username)
    {
        List<User> users = jdbcTemplate.query(
                "SELECT username, password_hash, role, enabled FROM app_user WHERE username = ?",
                (rs, rowNum) -> new User(
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        Role.valueOf(rs.getString("role")),
                        rs.getBoolean("enabled")),
                username);
        return users.stream().findFirst();
    }

    public List<User> findAll()
    {
        return jdbcTemplate.query(
                "SELECT username, password_hash, role, enabled FROM app_user ORDER BY username",
                (rs, rowNum) -> new User(
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        Role.valueOf(rs.getString("role")),
                        rs.getBoolean("enabled")));
    }

    public boolean exists(String username)
    {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE username = ?", Integer.class, username);
        return count != null && count > 0;
    }

    public long count()
    {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_user", Long.class);
        return count == null ? 0 : count;
    }

    public void save(User user)
    {
        int updated = jdbcTemplate.update(
                "UPDATE app_user SET password_hash = ?, role = ?, enabled = ? WHERE username = ?",
                user.passwordHash(), user.role().name(), user.enabled(), user.username());
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO app_user (username, password_hash, role, enabled, created_at) VALUES (?, ?, ?, ?, ?)",
                    user.username(), user.passwordHash(), user.role().name(), user.enabled(), Timestamp.from(Instant.now()));
        }
    }
}
