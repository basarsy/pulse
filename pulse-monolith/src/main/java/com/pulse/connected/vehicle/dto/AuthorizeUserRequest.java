package com.pulse.connected.vehicle.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AuthorizeUserRequest(
        @NotBlank(message = "User email is required")
        @Email(message = "Invalid email format")
        String userEmail) {
}
