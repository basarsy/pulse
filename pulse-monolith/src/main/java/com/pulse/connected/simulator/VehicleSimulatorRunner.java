package com.pulse.connected.simulator;

import com.pulse.connected.messaging.KafkaTopics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.connected.domain.CommandStatus;
import com.pulse.connected.messaging.dto.CommandResultEvent;
import com.pulse.connected.messaging.dto.OtaInstructionEvent;
import com.pulse.connected.messaging.dto.OtaUpdateProgressEvent;
import com.pulse.connected.messaging.dto.VehicleCommandEvent;
import com.pulse.connected.messaging.dto.VehicleHeartbeatEvent;
import com.pulse.connected.messaging.dto.VehicleTelemetryEvent;
import com.pulse.connected.domain.UpdateStatus;
import com.pulse.connected.domain.ConnectivityState;
import com.pulse.connected.vehicle.Vehicle;
import com.pulse.connected.vehicle.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Random;

@Component
@ConditionalOnProperty(name = "pulse.simulator.enabled", havingValue = "true", matchIfMissing = false)
public class VehicleSimulatorRunner {

    private static final Logger log = LoggerFactory.getLogger(VehicleSimulatorRunner.class);

    private final VehicleRepository vehicleRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    private final double commandFailureRate;
    private final double otaFailureRate;
    private final int minLatencyMs;
    private final int latencyJitterMs;
    private final long otaStepDelayMs;

    public VehicleSimulatorRunner(
            VehicleRepository vehicleRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${pulse.simulator.command-failure-rate:0.05}") double commandFailureRate,
            @Value("${pulse.simulator.ota-failure-rate:0.10}") double otaFailureRate,
            @Value("${pulse.simulator.min-latency-ms:1000}") int minLatencyMs,
            @Value("${pulse.simulator.latency-jitter-ms:1500}") int latencyJitterMs,
            @Value("${pulse.simulator.ota-step-delay-ms:500}") long otaStepDelayMs) {
        this.vehicleRepository = vehicleRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.commandFailureRate = commandFailureRate;
        this.otaFailureRate = otaFailureRate;
        this.minLatencyMs = minLatencyMs;
        this.latencyJitterMs = latencyJitterMs;
        this.otaStepDelayMs = otaStepDelayMs;
    }

    @Scheduled(fixedRateString = "${pulse.simulator.heartbeat-interval-ms:15000}")
    public void sendHeartbeats() {
        try {
            List<Vehicle> vehicles = vehicleRepository.findAll();
            for (Vehicle vehicle : vehicles) {
                VehicleHeartbeatEvent heartbeat = VehicleHeartbeatEvent.builder()
                        .vehicleId(vehicle.getId())
                        .vin(vehicle.getVin())
                        .state(ConnectivityState.ONLINE)
                        .timestamp(Instant.now())
                        .build();

                String payload = objectMapper.writeValueAsString(heartbeat);
                kafkaTemplate.send(KafkaTopics.VEHICLE_HEARTBEAT, vehicle.getId().toString(), payload);
            }
            if (!vehicles.isEmpty()) {
                log.debug("Simulator emitted heartbeats for {} virtual vehicles.", vehicles.size());
            }
        } catch (Exception e) {
            log.error("Simulator failed to publish heartbeats: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = KafkaTopics.VEHICLE_COMMANDS, groupId = "vehicle-simulator-group")
    public void handleIncomingCommand(String message) {
        try {
            VehicleCommandEvent event = objectMapper.readValue(message, VehicleCommandEvent.class);
            log.info("Simulator [Vehicle {}] received command [{}] type [{}]",
                    event.getVehicleId(), event.getCommandId(), event.getType());

            // Simulate cellular latency
            int delayMs = minLatencyMs + random.nextInt(latencyJitterMs + 1);
            Thread.sleep(delayMs);

            // Simulated ECU failure
            boolean success = random.nextDouble() >= commandFailureRate;
            CommandStatus resultStatus = success ? CommandStatus.COMPLETED : CommandStatus.FAILED;
            String resultMessage = success ?
                    "Executed " + event.getType() + " command successfully on ECU" :
                    "ECU returned error during execution of " + event.getType();

            CommandResultEvent resultEvent = CommandResultEvent.builder()
                    .commandId(event.getCommandId())
                    .vehicleId(event.getVehicleId())
                    .status(resultStatus)
                    .resultMessage(resultMessage)
                    .timestamp(Instant.now())
                    .build();

            String resultPayload = objectMapper.writeValueAsString(resultEvent);
            kafkaTemplate.send(KafkaTopics.VEHICLE_COMMAND_RESULTS, event.getVehicleId().toString(), resultPayload);
            log.info("Simulator [Vehicle {}] published result [{}] for command [{}] after {}ms",
                    event.getVehicleId(), resultStatus, event.getCommandId(), delayMs);

        } catch (Exception e) {
            log.error("Simulator error handling command message: {}", message, e);
        }
    }

    @KafkaListener(topics = KafkaTopics.OTA_INSTRUCTIONS, groupId = "vehicle-simulator-group")
    public void handleIncomingOtaInstruction(String message) {
        try {
            OtaInstructionEvent instruction = objectMapper.readValue(message, OtaInstructionEvent.class);
            log.info("Simulator [Vehicle {}] starting OTA update for version [{}]",
                    instruction.getVehicleId(), instruction.getTargetVersionLabel());

            // 1. Downloading 25%
            publishOtaProgress(instruction, UpdateStatus.DOWNLOADING, 25, null);
            Thread.sleep(otaStepDelayMs);

            // 2. Downloading 100% -> Downloaded
            publishOtaProgress(instruction, UpdateStatus.DOWNLOADING, 100, null);
            publishOtaProgress(instruction, UpdateStatus.DOWNLOADED, 100, null);
            Thread.sleep(otaStepDelayMs);

            // 3. Installing 50%
            publishOtaProgress(instruction, UpdateStatus.INSTALLING, 50, null);
            Thread.sleep(otaStepDelayMs);

            // 4. Simulated flashing failure
            boolean success = random.nextDouble() >= otaFailureRate;

            if (success) {
                publishOtaProgress(instruction, UpdateStatus.INSTALLED, 100, null);
                log.info("Simulator [Vehicle {}] successfully INSTALLED version [{}]",
                        instruction.getVehicleId(), instruction.getTargetVersionLabel());
            } else {
                publishOtaProgress(instruction, UpdateStatus.FAILED, 50, "ECU checksum verification failed during flashing");
                log.warn("Simulator [Vehicle {}] FAILED installing version [{}]",
                        instruction.getVehicleId(), instruction.getTargetVersionLabel());
            }

        } catch (Exception e) {
            log.error("Simulator error handling OTA instruction message: {}", message, e);
        }
    }

    private void publishOtaProgress(OtaInstructionEvent instruction, UpdateStatus status, int progress, String errorMessage) throws Exception {
        OtaUpdateProgressEvent progressEvent = OtaUpdateProgressEvent.builder()
                .updateStatusId(instruction.getUpdateStatusId())
                .vehicleId(instruction.getVehicleId())
                .campaignId(instruction.getCampaignId())
                .status(status)
                .progressPercent(progress)
                .errorMessage(errorMessage)
                .timestamp(Instant.now())
                .build();

        String payload = objectMapper.writeValueAsString(progressEvent);
        kafkaTemplate.send(KafkaTopics.OTA_UPDATE_STATUS, instruction.getVehicleId().toString(), payload);
    }

    @Scheduled(fixedRateString = "${pulse.simulator.telemetry-interval-ms:20000}")
    public void emitTelemetry() {
        try {
            List<Vehicle> vehicles = vehicleRepository.findAll();
            for (Vehicle vehicle : vehicles) {
                double newBatteryLevel = Math.max(0.0, (vehicle.getBatteryOrFuelLevel() != null ? vehicle.getBatteryOrFuelLevel() : 100.0) - (0.1 + (0.4 * random.nextDouble())));
                double newOdometer = (vehicle.getOdometerKm() != null ? vehicle.getOdometerKm() : 0.0) + (0.5 + (1.5 * random.nextDouble()));
                double engineTemp = 85.0 + (20.0 * random.nextDouble());
                double lat = 48.177 + (-0.01 + (0.02 * random.nextDouble()));
                double lng = 11.556 + (-0.01 + (0.02 * random.nextDouble()));

                VehicleTelemetryEvent telemetryEvent = VehicleTelemetryEvent.builder()
                        .vehicleId(vehicle.getId())
                        .batteryOrFuelLevel(newBatteryLevel)
                        .odometerKm(newOdometer)
                        .engineTempC(engineTemp)
                        .lat(lat)
                        .lng(lng)
                        .timestamp(Instant.now())
                        .build();

                String payload = objectMapper.writeValueAsString(telemetryEvent);
                kafkaTemplate.send(KafkaTopics.VEHICLE_TELEMETRY, vehicle.getId().toString(), payload);
            }
            if (!vehicles.isEmpty()) {
                log.debug("Simulator emitted telemetry for {} virtual vehicles.", vehicles.size());
            }
        } catch (Exception e) {
            log.error("Simulator failed to publish telemetry: {}", e.getMessage(), e);
        }
    }
}
