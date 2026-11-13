package com.pulse.connected.vehicle;

import java.util.UUID;

/**
 * Published when a vehicle comes back ONLINE after being OFFLINE or SLEEPING.
 * Other modules (e.g. command) react to it without the vehicle module depending on them.
 */
public record VehicleReconnectedEvent(UUID vehicleId) {
}
