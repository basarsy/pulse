package com.pulse.connected.command.dto;

import com.pulse.connected.domain.CommandType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IssueCommandRequest(
        @NotNull(message = "Command type is required")
        CommandType type,

        @Size(max = 100, message = "Idempotency key must be at most 100 characters")
        String idempotencyKey) {
}
