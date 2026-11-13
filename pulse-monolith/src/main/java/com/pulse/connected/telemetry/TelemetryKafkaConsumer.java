package com.pulse.connected.telemetry;

import com.pulse.connected.messaging.KafkaTopics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.connected.messaging.dto.VehicleTelemetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TelemetryKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryKafkaConsumer.class);

    private final ObjectMapper objectMapper;
    private final TelemetryService telemetryService;

    public TelemetryKafkaConsumer(ObjectMapper objectMapper, TelemetryService telemetryService) {
        this.objectMapper = objectMapper;
        this.telemetryService = telemetryService;
    }

    @KafkaListener(topics = KafkaTopics.VEHICLE_TELEMETRY, groupId = "backend-service-group")
    public void consumeTelemetry(String message) throws JsonProcessingException {
        // Exceptions propagate to the shared error handler (retry, then dead-letter topic).
        VehicleTelemetryEvent event = objectMapper.readValue(message, VehicleTelemetryEvent.class);
        telemetryService.ingestTelemetry(event);
        log.debug("Consumed telemetry event for vehicle: {}", event.getVehicleId());
    }
}
