package com.pulse.connected.ota;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.connected.messaging.KafkaPublishException;
import com.pulse.connected.messaging.KafkaTopics;
import com.pulse.connected.messaging.dto.OtaInstructionEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Publishes OTA instructions to vehicles. Sends are synchronous and failures are thrown, so a
 * failed send leaves the outbox publication incomplete and it is retried (see {@link OtaKafkaRelay}).
 */
@Service
public class OtaKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(OtaKafkaProducer.class);
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OtaKafkaProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @CircuitBreaker(name = "kafkaCircuitBreaker")
    @Retry(name = "kafkaRetry")
    public void dispatchOtaInstruction(OtaInstructionEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            SendResult<String, String> result = kafkaTemplate
                    .send(KafkaTopics.OTA_INSTRUCTIONS, event.getVehicleId().toString(), payload)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("Dispatched OTA instruction [{}] version [{}] to vehicle [{}] on partition [{}]",
                    event.getUpdateStatusId(), event.getTargetVersionLabel(), event.getVehicleId(),
                    result.getRecordMetadata().partition());
        } catch (JsonProcessingException e) {
            throw new KafkaPublishException("Could not serialise OTA instruction " + event.getUpdateStatusId(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KafkaPublishException("Interrupted while sending OTA instruction " + event.getUpdateStatusId(), e);
        } catch (Exception e) {
            throw new KafkaPublishException("Failed to send OTA instruction " + event.getUpdateStatusId(), e);
        }
    }
}
