package com.pulse.connected.auth.dto;

import com.pulse.connected.auth.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Full name is required")
    private String fullName;

    private Set<Role> roles;

    public RegisterRequest() {}

    public RegisterRequest(String email, String password, String fullName, Set<Role> roles) {
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.roles = roles;
    }

    public static RegisterRequestBuilder builder() {
        return new RegisterRequestBuilder();
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }

    public static class RegisterRequestBuilder {
        private String email;
        private String password;
        private String fullName;
        private Set<Role> roles;

        RegisterRequestBuilder() {}

        public RegisterRequestBuilder email(String email) { this.email = email; return this; }
        public RegisterRequestBuilder password(String password) { this.password = password; return this; }
        public RegisterRequestBuilder fullName(String fullName) { this.fullName = fullName; return this; }
        public RegisterRequestBuilder roles(Set<Role> roles) { this.roles = roles; return this; }

        public RegisterRequest build() {
            return new RegisterRequest(email, password, fullName, roles);
        }
    }
}
