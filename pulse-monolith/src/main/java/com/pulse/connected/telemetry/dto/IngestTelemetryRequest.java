package com.pulse.connected.telemetry.dto;

public record IngestTelemetryRequest(
        Double batteryOrFuelLevel,
        Double odometerKm,
        Double engineTempC,
        Double lat,
        Double lng) {
}
