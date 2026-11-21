package com.pulse.connected.command;

import com.pulse.connected.vehicle.VehicleReconnectedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Flushes commands that were queued while a vehicle was offline once it reconnects.
 *
 * <p>An application module listener: it runs after the heartbeat's transaction commits, in its own
 * transaction, and the event is recorded in the outbox. If the flush fails (e.g. a cancel changed
 * one of the commands meanwhile), the publication stays incomplete and is resubmitted, rather than
 * being lost because the retried heartbeat finds the vehicle already online.
 * {@link PendingCommandSweep} also catches commands that missed both.
 */
@Component
class VehicleReconnectedListener {

    private static final Logger log = LoggerFactory.getLogger(VehicleReconnectedListener.class);

    private final CommandService commandService;

    VehicleReconnectedListener(CommandService commandService) {
        this.commandService = commandService;
    }

    @ApplicationModuleListener
    void on(VehicleReconnectedEvent event) {
        log.info("Flushing queued pending commands for reconnected vehicle [{}]", event.vehicleId());
        commandService.dispatchPendingCommandsForVehicle(event.vehicleId());
    }
}
