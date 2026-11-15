package com.pulse.connected.vehicle.dto;

import com.pulse.connected.domain.ConnectivityState;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record VehicleResponse(
        UUID id,
        String vin,
        String model,
        Integer modelYear,
        UUID ownerId,
        String ownerEmail,
        Set<UUID> authorizedUserIds,
        ConnectivityState connectivityState,
        String currentSoftwareVersion,
        Instant lastSeenAt,
        Double batteryOrFuelLevel,
        Double odometerKm,
        Double lastLat,
        Double lastLng,
        Instant createdAt) {
}
