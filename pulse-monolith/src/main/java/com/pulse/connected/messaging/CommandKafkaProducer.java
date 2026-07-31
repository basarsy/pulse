package com.pulse.connected.messaging;

import com.pulse.connected.messaging.KafkaTopics;

import com.pulse.connected.messaging.KafkaTopics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.connected.messaging.dto.VehicleCommandEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@Service
public class CommandKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(CommandKafkaProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public CommandKafkaProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @CircuitBreaker(name = "kafkaCircuitBreaker", fallbackMethod = "dispatchCommandFallback")
    @Retry(name = "kafkaRetry")
    public void dispatchCommand(VehicleCommandEvent event) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(event);
            String partitionKey = event.getVehicleId().toString();
            
            kafkaTemplate.send(KafkaTopics.VEHICLE_COMMANDS, partitionKey, jsonPayload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to send command [{}] to vehicle [{}]: {}", 
                                    event.getCommandId(), event.getVehicleId(), ex.getMessage(), ex);
                        } else {
                            log.info("Dispatched command [{}] type [{}] to vehicle [{}] on partition [{}]",
                                    event.getCommandId(), event.getType(), event.getVehicleId(),
                                    result.getRecordMetadata().partition());
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("Error serializing VehicleCommandEvent [{}]: {}", event.getCommandId(), e.getMessage(), e);
        }
    }
    
    public void dispatchCommandFallback(VehicleCommandEvent event, Throwable t) {
        log.warn("Fallback triggered for dispatchCommand for command [{}] due to: {}", event.getCommandId(), t.getMessage());
    }
}
