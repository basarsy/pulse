package com.pulse.connected.vehicle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Applies a vehicle's heartbeat: marks it ONLINE and seen now.
 *
 * <p>Only those two columns are written ({@link VehicleRepository#markOnline}), never the whole row,
 * so a heartbeat can't put back an older software version or telemetry reading written meanwhile by
 * another listener.
 *
 * <p>A heartbeat that brings the vehicle back online publishes {@link VehicleReconnectedEvent} in
 * this transaction. Its listeners are application module listeners, so Spring Modulith records the
 * event with the transaction and retries a listener that fails: a failed flush of queued commands
 * isn't lost when the next heartbeat finds the vehicle already online.
 */
@Service
class VehicleHeartbeats {

    private static final Logger log = LoggerFactory.getLogger(VehicleHeartbeats.class);

    private final VehicleRepository vehicleRepository;
    private final ApplicationEventPublisher eventPublisher;

    VehicleHeartbeats(VehicleRepository vehicleRepository, ApplicationEventPublisher eventPublisher) {
        this.vehicleRepository = vehicleRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void record(UUID vehicleId) {
        Instant now = Instant.now();
        if (vehicleRepository.markOnline(vehicleId, now) == 1) {
            log.info("Vehicle [{}] reconnected (state: ONLINE).", vehicleId);
            eventPublisher.publishEvent(new VehicleReconnectedEvent(vehicleId));
        } else if (vehicleRepository.markSeen(vehicleId, now) == 0) {
            log.warn("Heartbeat received for unknown vehicle ID: {}", vehicleId);
        }
    }
}
