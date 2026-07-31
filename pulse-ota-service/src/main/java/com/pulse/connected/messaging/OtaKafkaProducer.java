package com.pulse.connected.messaging;

import com.pulse.connected.messaging.dto.OtaInstructionEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OtaKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(OtaKafkaProducer.class);
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OtaKafkaProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @CircuitBreaker(name = "kafkaCircuitBreaker", fallbackMethod = "sendInstructionFallback")
    @Retry(name = "kafkaRetry")
    public void sendOtaInstruction(OtaInstructionEvent event) {
        log.info("Publishing OTA instruction for vehicle [{}] campaign [{}] target [{}]",
                event.getVehicleId(), event.getCampaignId(), event.getTargetVersionLabel());

        kafkaTemplate.send(KafkaTopics.OTA_INSTRUCTIONS, event.getVehicleId().toString(), event);
    }

    public void sendInstructionFallback(OtaInstructionEvent event, Throwable t) {
        log.error("FALLBACK: Failed to send OTA instruction for vehicle [{}] via Kafka. Reason: {}",
                event.getVehicleId(), t.getMessage());
    }
}
