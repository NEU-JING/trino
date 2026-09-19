package io.trino.datafabric.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHasherTest
{
    @Test
    void verifyAcceptsCorrectPassword()
    {
        String encoded = PasswordHasher.hash("s3cret");
        assertThat(PasswordHasher.verify("s3cret", encoded)).isTrue();
    }

    @Test
    void verifyRejectsWrongPassword()
    {
        String encoded = PasswordHasher.hash("s3cret");
        assertThat(PasswordHasher.verify("nope", encoded)).isFalse();
    }

    @Test
    void hashesAreSalted()
    {
        assertThat(PasswordHasher.hash("same")).isNotEqualTo(PasswordHasher.hash("same"));
    }

    @Test
    void verifyRejectsMalformedHash()
    {
        assertThat(PasswordHasher.verify("x", "not-a-hash")).isFalse();
    }
}
