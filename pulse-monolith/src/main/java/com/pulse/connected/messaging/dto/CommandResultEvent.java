package com.pulse.connected.messaging.dto;

import com.pulse.connected.domain.CommandStatus;

import java.time.Instant;
import java.util.UUID;

public record CommandResultEvent(
        UUID commandId,
        UUID vehicleId,
        CommandStatus status,
        String resultMessage,
        Instant timestamp) {
}
