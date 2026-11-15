package com.pulse.connected.vehicle;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Marks vehicles OFFLINE once their heartbeats stop. Without this a vehicle stays ONLINE forever
 * after its first heartbeat, commands are sent to vehicles that are not listening, and the
 * queue-and-flush-on-reconnect path ({@link VehicleReconnectedEvent}) never runs.
 */
@Component
class VehicleConnectivityMonitor {

    private static final Logger log = LoggerFactory.getLogger(VehicleConnectivityMonitor.class);

    private final VehicleRepository vehicleRepository;
    private final ConnectivityProperties properties;

    VehicleConnectivityMonitor(VehicleRepository vehicleRepository, ConnectivityProperties properties) {
        this.vehicleRepository = vehicleRepository;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${pulse.connectivity.check-interval-ms:15000}")
    @SchedulerLock(name = "connectivity-offline-check",
            lockAtLeastFor = "#{${pulse.connectivity.check-interval-ms:15000} * 8 / 10}")
    @Transactional
    public void markSilentVehiclesOffline() {
        int marked = vehicleRepository.markOnlineVehiclesOfflineLastSeenBefore(
                Instant.now().minus(properties.offlineAfter()));
        if (marked > 0) {
            log.info("Marked {} silent vehicle(s) OFFLINE (no heartbeat for {})", marked, properties.offlineAfter());
        }
    }
}
