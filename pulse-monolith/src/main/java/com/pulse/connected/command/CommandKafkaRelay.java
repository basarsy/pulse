package com.pulse.connected.command;

import com.pulse.connected.messaging.dto.VehicleCommandEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Outbox relay for commands. {@link CommandService} publishes a {@link VehicleCommandEvent} inside
 * its transaction; Spring Modulith persists the publication in the same transaction and invokes
 * this listener only after commit. If sending fails, the publication stays incomplete and is
 * resubmitted (see {@code OutboxMaintenance}), so commands are neither sent for rolled-back
 * transactions nor lost after successful ones.
 */
@Component
class CommandKafkaRelay {

    private final CommandKafkaProducer producer;

    CommandKafkaRelay(CommandKafkaProducer producer) {
        this.producer = producer;
    }

    @ApplicationModuleListener
    void on(VehicleCommandEvent event) {
        producer.dispatchCommand(event);
    }
}
