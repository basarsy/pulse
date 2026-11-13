package com.pulse.connected.auth;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-bytes-long!!";

    private final JwtService jwtService = new JwtService(SECRET, 60_000, 120_000);

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("driver@pulse.test");
        user.setFullName("Test Driver");
        user.setRoles(Set.of(Role.OWNER));
        return user;
    }

    @Test
    void accessToken_IsAcceptedAsAccessToken() {
        User user = user();
        String token = jwtService.generateAccessToken(user);

        assertThat(jwtService.parseAccessToken(token)).contains(user.getId());
    }

    @Test
    void refreshToken_IsRejectedAsAccessToken() {
        String refreshToken = jwtService.generateRefreshToken(user());

        assertThat(jwtService.parseAccessToken(refreshToken)).isEmpty();
    }

    @Test
    void accessToken_IsRejectedAsRefreshToken() {
        String accessToken = jwtService.generateAccessToken(user());

        assertThat(jwtService.parseRefreshToken(accessToken)).isEmpty();
    }

    @Test
    void refreshToken_IsAcceptedAsRefreshToken() {
        User user = user();
        String token = jwtService.generateRefreshToken(user);

        assertThat(jwtService.parseRefreshToken(token)).contains(user.getId());
    }

    @Test
    void tokenSignedWithAnotherKey_IsRejected() {
        JwtService other = new JwtService("another-secret-key-that-is-also-32-bytes-long!", 60_000, 120_000);
        String foreignToken = other.generateAccessToken(user());

        assertThat(jwtService.parseAccessToken(foreignToken)).isEmpty();
    }

    @Test
    void garbageToken_IsRejected() {
        assertThat(jwtService.parseAccessToken("not-a-jwt")).isEmpty();
    }
}
