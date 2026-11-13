package com.pulse.connected.ota;

import com.pulse.connected.messaging.dto.OtaInstructionEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Outbox relay for OTA instructions: sends after the rollout transaction has committed, so a
 * vehicle can never report progress for an update-status row that does not exist yet.
 */
@Component
class OtaKafkaRelay {

    private final OtaKafkaProducer producer;

    OtaKafkaRelay(OtaKafkaProducer producer) {
        this.producer = producer;
    }

    @ApplicationModuleListener
    void on(OtaInstructionEvent event) {
        producer.dispatchOtaInstruction(event);
    }
}
