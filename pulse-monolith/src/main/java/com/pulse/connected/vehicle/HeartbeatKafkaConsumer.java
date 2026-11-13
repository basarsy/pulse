package com.pulse.connected.vehicle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.connected.domain.ConnectivityState;
import com.pulse.connected.messaging.KafkaTopics;
import com.pulse.connected.messaging.dto.VehicleHeartbeatEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
public class HeartbeatKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatKafkaConsumer.class);

    private final ObjectMapper objectMapper;
    private final VehicleRepository vehicleRepository;
    private final ApplicationEventPublisher eventPublisher;

    public HeartbeatKafkaConsumer(ObjectMapper objectMapper, VehicleRepository vehicleRepository,
                                  ApplicationEventPublisher eventPublisher) {
        this.objectMapper = objectMapper;
        this.vehicleRepository = vehicleRepository;
        this.eventPublisher = eventPublisher;
    }

    @KafkaListener(topics = KafkaTopics.VEHICLE_HEARTBEAT, groupId = "backend-service-group")
    public void consumeHeartbeat(String message) throws JsonProcessingException {
        // Exceptions propagate to the shared error handler (retry, then dead-letter topic).
        VehicleHeartbeatEvent event = objectMapper.readValue(message, VehicleHeartbeatEvent.class);

        Optional<Vehicle> vehicleOpt = vehicleRepository.findById(event.getVehicleId());
        if (vehicleOpt.isEmpty()) {
            log.warn("Heartbeat received for unknown vehicle ID: {}", event.getVehicleId());
            return;
        }

        Vehicle vehicle = vehicleOpt.get();
        ConnectivityState previousState = vehicle.getConnectivityState();

        vehicle.setConnectivityState(ConnectivityState.ONLINE);
        vehicle.setLastSeenAt(Instant.now());
        vehicleRepository.save(vehicle);

        if (previousState == ConnectivityState.OFFLINE || previousState == ConnectivityState.SLEEPING) {
            log.info("Vehicle [{}] reconnected (state: ONLINE).", vehicle.getId());
            eventPublisher.publishEvent(new VehicleReconnectedEvent(vehicle.getId()));
        }
    }
}
