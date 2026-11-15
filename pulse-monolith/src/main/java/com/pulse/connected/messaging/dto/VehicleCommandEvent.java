package com.pulse.connected.messaging.dto;

import com.pulse.connected.domain.CommandType;

import java.time.Instant;
import java.util.UUID;

public record VehicleCommandEvent(
        UUID commandId,
        UUID vehicleId,
        CommandType type,
        Instant issuedAt,
        String idempotencyKey) {
}
