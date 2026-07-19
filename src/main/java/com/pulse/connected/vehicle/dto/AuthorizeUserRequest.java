package com.pulse.connected.vehicle.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class AuthorizeUserRequest {

    @NotBlank(message = "User email is required")
    @Email(message = "Invalid email format")
    private String userEmail;

    public AuthorizeUserRequest() {}

    public AuthorizeUserRequest(String userEmail) {
        this.userEmail = userEmail;
    }

    public static AuthorizeUserRequestBuilder builder() {
        return new AuthorizeUserRequestBuilder();
    }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public static class AuthorizeUserRequestBuilder {
        private String userEmail;

        AuthorizeUserRequestBuilder() {}

        public AuthorizeUserRequestBuilder userEmail(String userEmail) { this.userEmail = userEmail; return this; }

        public AuthorizeUserRequest build() {
            return new AuthorizeUserRequest(userEmail);
        }
    }
}
