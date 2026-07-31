package com.pulse.connected.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.pulse.connected.messaging.dto.VehicleHeartbeatEvent;
import com.pulse.connected.domain.ConnectivityState;
import com.pulse.connected.vehicle.Vehicle;
import com.pulse.connected.vehicle.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
public class HeartbeatKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatKafkaConsumer.class);

    private final ObjectMapper objectMapper;
    private final VehicleRepository vehicleRepository;
    public HeartbeatKafkaConsumer(ObjectMapper objectMapper, VehicleRepository vehicleRepository) {
        this.objectMapper = objectMapper;
        this.vehicleRepository = vehicleRepository;
    }

    @KafkaListener(topics = KafkaTopics.VEHICLE_HEARTBEAT, groupId = "vehicle-registry-group")
    public void consumeHeartbeat(String message) {
        try {
            VehicleHeartbeatEvent event = objectMapper.readValue(message, VehicleHeartbeatEvent.class);
            
            Optional<Vehicle> vehicleOpt = vehicleRepository.findById(event.getVehicleId());
            if (vehicleOpt.isPresent()) {
                Vehicle vehicle = vehicleOpt.get();
                ConnectivityState previousState = vehicle.getConnectivityState();
                
                vehicle.setConnectivityState(ConnectivityState.ONLINE);
                vehicle.setLastSeenAt(Instant.now());
                vehicleRepository.save(vehicle);

                if (previousState == ConnectivityState.OFFLINE || previousState == ConnectivityState.SLEEPING) {
                    log.info("Vehicle [{}] reconnected (state: ONLINE).", vehicle.getId());
                }
            } else {
                log.warn("Heartbeat received for unknown vehicle ID: {}", event.getVehicleId());
            }
        } catch (Exception e) {
            log.error("Failed to process heartbeat message: {}", message, e);
        }
    }
}
