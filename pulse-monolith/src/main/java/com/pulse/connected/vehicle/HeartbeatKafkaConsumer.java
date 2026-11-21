package com.pulse.connected.vehicle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.connected.messaging.KafkaTopics;
import com.pulse.connected.messaging.dto.VehicleHeartbeatEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class HeartbeatKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final VehicleHeartbeats heartbeats;

    HeartbeatKafkaConsumer(ObjectMapper objectMapper, VehicleHeartbeats heartbeats) {
        this.objectMapper = objectMapper;
        this.heartbeats = heartbeats;
    }

    @KafkaListener(topics = KafkaTopics.VEHICLE_HEARTBEAT, groupId = "backend-service-group")
    public void consumeHeartbeat(String message) throws JsonProcessingException {
        // Exceptions propagate to the shared error handler (retry, then dead-letter topic).
        VehicleHeartbeatEvent event = objectMapper.readValue(message, VehicleHeartbeatEvent.class);
        heartbeats.record(event.vehicleId());
    }
}
