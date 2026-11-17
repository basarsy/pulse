package com.pulse.connected.ota.dto;

import com.pulse.connected.domain.UpdateStatus;

import java.time.Instant;
import java.util.UUID;

public record VehicleUpdateStatusResponse(
        UUID id,
        UUID vehicleId,
        String vin,
        UUID campaignId,
        UUID targetVersionId,
        String targetVersionLabel,
        UpdateStatus status,
        Integer progressPercent,
        Instant startedAt,
        Instant completedAt,
        String errorMessage,
        long version) {
}
