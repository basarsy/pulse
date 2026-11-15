package com.pulse.connected.messaging.dto;

import com.pulse.connected.domain.UpdateStatus;

import java.time.Instant;
import java.util.UUID;

public record OtaUpdateProgressEvent(
        UUID updateStatusId,
        UUID vehicleId,
        UUID campaignId,
        UpdateStatus status,
        Integer progressPercent,
        String errorMessage,
        Instant timestamp) {
}
