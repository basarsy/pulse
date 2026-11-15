package com.pulse.connected.telemetry.dto;

import java.time.Instant;
import java.util.UUID;

public record TelemetrySnapshotResponse(
        UUID id,
        UUID vehicleId,
        Instant recordedAt,
        Double batteryOrFuelLevel,
        Double odometerKm,
        Double engineTempC,
        Double lat,
        Double lng) {
}
