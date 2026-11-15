package com.pulse.connected.command.dto;

import com.pulse.connected.domain.CommandStatus;
import com.pulse.connected.domain.CommandType;

import java.time.Instant;
import java.util.UUID;

public record CommandResponse(
        UUID id,
        UUID vehicleId,
        UUID issuedByUserId,
        CommandType type,
        CommandStatus status,
        Instant requestedAt,
        Instant sentAt,
        Instant resolvedAt,
        String resultMessage,
        String idempotencyKey) {
}
