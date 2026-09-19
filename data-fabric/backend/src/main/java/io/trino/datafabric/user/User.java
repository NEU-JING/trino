package io.trino.datafabric.user;

import io.trino.datafabric.security.Role;

public record User(String username, String passwordHash, Role role, boolean enabled) {}
