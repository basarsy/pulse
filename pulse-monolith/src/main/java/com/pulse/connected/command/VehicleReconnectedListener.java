package com.pulse.connected.command;

import com.pulse.connected.vehicle.VehicleReconnectedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Flushes commands that were queued while a vehicle was offline once it reconnects.
 */
@Component
class VehicleReconnectedListener {

    private static final Logger log = LoggerFactory.getLogger(VehicleReconnectedListener.class);

    private final CommandService commandService;

    VehicleReconnectedListener(CommandService commandService) {
        this.commandService = commandService;
    }

    @EventListener
    void on(VehicleReconnectedEvent event) {
        log.info("Flushing queued pending commands for reconnected vehicle [{}]", event.vehicleId());
        commandService.dispatchPendingCommandsForVehicle(event.vehicleId());
    }
}
