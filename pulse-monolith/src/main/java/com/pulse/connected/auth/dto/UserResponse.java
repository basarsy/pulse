package com.pulse.connected.auth.dto;

import com.pulse.connected.auth.Role;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        Set<Role> roles,
        Instant createdAt) {
}
