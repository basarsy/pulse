package com.pulse.connected.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;

    public RefreshTokenRequest() {}

    public RefreshTokenRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public static RefreshTokenRequestBuilder builder() {
        return new RefreshTokenRequestBuilder();
    }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public static class RefreshTokenRequestBuilder {
        private String refreshToken;

        RefreshTokenRequestBuilder() {}

        public RefreshTokenRequestBuilder refreshToken(String refreshToken) { this.refreshToken = refreshToken; return this; }

        public RefreshTokenRequest build() {
            return new RefreshTokenRequest(refreshToken);
        }
    }
}
