package com.pulse.connected.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.connected.messaging.KafkaPublishException;
import com.pulse.connected.messaging.KafkaTopics;
import com.pulse.connected.messaging.dto.VehicleCommandEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Publishes commands to vehicles. Sends are synchronous and failures are thrown, so a failed
 * send leaves the outbox publication incomplete and it is retried (see {@link CommandKafkaRelay}).
 */
@Service
public class CommandKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(CommandKafkaProducer.class);
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public CommandKafkaProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @CircuitBreaker(name = "kafkaCircuitBreaker")
    @Retry(name = "kafkaRetry")
    public void dispatchCommand(VehicleCommandEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            SendResult<String, String> result = kafkaTemplate
                    .send(KafkaTopics.VEHICLE_COMMANDS, event.getVehicleId().toString(), payload)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("Dispatched command [{}] type [{}] to vehicle [{}] on partition [{}]",
                    event.getCommandId(), event.getType(), event.getVehicleId(),
                    result.getRecordMetadata().partition());
        } catch (JsonProcessingException e) {
            throw new KafkaPublishException("Could not serialise command " + event.getCommandId(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KafkaPublishException("Interrupted while sending command " + event.getCommandId(), e);
        } catch (Exception e) {
            throw new KafkaPublishException("Failed to send command " + event.getCommandId(), e);
        }
    }
}
