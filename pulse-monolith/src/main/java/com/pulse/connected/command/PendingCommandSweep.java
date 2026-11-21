package com.pulse.connected.command;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Sends queued commands of vehicles that are already online. Normally the reconnect flush
 * ({@link VehicleReconnectedListener}) sends them, but a command can miss it: one issued while the
 * vehicle still looked offline, whose transaction commits just after the flush read the queue. Such
 * a command would otherwise wait until it expires although the vehicle is listening.
 *
 * <p>Each vehicle is flushed in its own transaction. If the reconnect flush handles the same vehicle
 * at the same moment, optimistic locking on the commands makes one of the two roll back, so no
 * command is sent twice.
 */
@Component
class PendingCommandSweep {

    private static final Logger log = LoggerFactory.getLogger(PendingCommandSweep.class);

    private final RemoteCommandRepository commandRepository;
    private final CommandService commandService;

    PendingCommandSweep(RemoteCommandRepository commandRepository, CommandService commandService) {
        this.commandRepository = commandRepository;
        this.commandService = commandService;
    }

    @Scheduled(fixedDelayString = "${pulse.commands.pending-sweep-interval-ms:30000}")
    @SchedulerLock(name = "command-pending-flush",
            lockAtLeastFor = "#{${pulse.commands.pending-sweep-interval-ms:30000}L * 8 / 10}",
            lockAtMostFor = "#{T(java.lang.Math).max(${pulse.commands.pending-sweep-interval-ms:30000}L, 300000L)}")
    void flushPendingCommandsOfOnlineVehicles() {
        sweep();
    }

    /** One sweep, without the lock (the scheduled entry point above holds it). */
    void sweep() {
        List<UUID> vehicleIds = commandRepository.findOnlineVehicleIdsWithPendingCommands();
        for (UUID vehicleId : vehicleIds) {
            try {
                log.info("Vehicle [{}] is online but has queued commands; sending them", vehicleId);
                commandService.dispatchPendingCommandsForVehicle(vehicleId);
            } catch (RuntimeException e) {
                // Most likely the reconnect flush won the race for these commands; the next run retries.
                log.warn("Could not flush queued commands of vehicle [{}]: {}", vehicleId, e.toString());
            }
        }
    }
}
