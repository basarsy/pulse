package com.pulse.connected.messaging.dto;

import com.pulse.connected.domain.ConnectivityState;

import java.time.Instant;
import java.util.UUID;

public record VehicleHeartbeatEvent(
        UUID vehicleId,
        String vin,
        ConnectivityState state,
        Instant timestamp) {
}
