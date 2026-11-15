package com.pulse.connected.messaging.dto;

import java.time.Instant;
import java.util.UUID;

public record VehicleTelemetryEvent(
        UUID vehicleId,
        Double batteryOrFuelLevel,
        Double odometerKm,
        Double engineTempC,
        Double lat,
        Double lng,
        Instant timestamp) {
}
