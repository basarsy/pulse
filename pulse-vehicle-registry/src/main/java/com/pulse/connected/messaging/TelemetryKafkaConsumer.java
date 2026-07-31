package com.pulse.connected.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.connected.messaging.dto.VehicleTelemetryEvent;
import com.pulse.connected.telemetry.TelemetryService;
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

    @KafkaListener(topics = KafkaTopics.VEHICLE_TELEMETRY, groupId = "vehicle-registry-group")
    public void consumeTelemetry(String message) {
        try {
            VehicleTelemetryEvent event = objectMapper.readValue(message, VehicleTelemetryEvent.class);
            telemetryService.ingestTelemetry(event);
            log.debug("Consumed telemetry event for vehicle: {}", event.getVehicleId());
        } catch (Exception e) {
            log.error("Failed to process telemetry message: {}", message, e);
        }
    }
}
