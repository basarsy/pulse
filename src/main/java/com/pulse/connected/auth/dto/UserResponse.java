package com.pulse.connected.auth.dto;

import com.pulse.connected.auth.Role;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public class UserResponse {
    private UUID id;
    private String email;
    private String fullName;
    private Set<Role> roles;
    private Instant createdAt;

    public UserResponse() {}

    public UserResponse(UUID id, String email, String fullName, Set<Role> roles, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.roles = roles;
        this.createdAt = createdAt;
    }

    public static UserResponseBuilder builder() {
        return new UserResponseBuilder();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static class UserResponseBuilder {
        private UUID id;
        private String email;
        private String fullName;
        private Set<Role> roles;
        private Instant createdAt;

        UserResponseBuilder() {}

        public UserResponseBuilder id(UUID id) { this.id = id; return this; }
        public UserResponseBuilder email(String email) { this.email = email; return this; }
        public UserResponseBuilder fullName(String fullName) { this.fullName = fullName; return this; }
        public UserResponseBuilder roles(Set<Role> roles) { this.roles = roles; return this; }
        public UserResponseBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public UserResponse build() {
            return new UserResponse(id, email, fullName, roles, createdAt);
        }
    }
}
