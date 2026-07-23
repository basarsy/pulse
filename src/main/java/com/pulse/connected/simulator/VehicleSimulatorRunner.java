package com.pulse.connected.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.connected.command.CommandStatus;
import com.pulse.connected.messaging.KafkaTopicConfig;
import com.pulse.connected.messaging.dto.CommandResultEvent;
import com.pulse.connected.messaging.dto.OtaInstructionEvent;
import com.pulse.connected.messaging.dto.OtaUpdateProgressEvent;
import com.pulse.connected.messaging.dto.VehicleCommandEvent;
import com.pulse.connected.messaging.dto.VehicleHeartbeatEvent;
import com.pulse.connected.ota.UpdateStatus;
import com.pulse.connected.vehicle.ConnectivityState;
import com.pulse.connected.vehicle.Vehicle;
import com.pulse.connected.vehicle.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Random;

@Component
@ConditionalOnProperty(name = "pulse.simulator.enabled", havingValue = "true", matchIfMissing = true)
public class VehicleSimulatorRunner {

    private static final Logger log = LoggerFactory.getLogger(VehicleSimulatorRunner.class);

    private final VehicleRepository vehicleRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    public VehicleSimulatorRunner(
            VehicleRepository vehicleRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.vehicleRepository = vehicleRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedRate = 15000)
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
                kafkaTemplate.send(KafkaTopicConfig.TOPIC_VEHICLE_HEARTBEAT, vehicle.getId().toString(), payload);
            }
            if (!vehicles.isEmpty()) {
                log.debug("Simulator emitted heartbeats for {} virtual vehicles.", vehicles.size());
            }
        } catch (Exception e) {
            log.error("Simulator failed to publish heartbeats: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = KafkaTopicConfig.TOPIC_VEHICLE_COMMANDS, groupId = "vehicle-simulator-group")
    public void handleIncomingCommand(String message) {
        try {
            VehicleCommandEvent event = objectMapper.readValue(message, VehicleCommandEvent.class);
            log.info("Simulator [Vehicle {}] received command [{}] type [{}]",
                    event.getVehicleId(), event.getCommandId(), event.getType());

            // Simulate cellular latency delay (1-2.5s)
            int delayMs = 1000 + random.nextInt(1500);
            Thread.sleep(delayMs);

            // 95% success, 5% failure simulation
            boolean success = random.nextDouble() > 0.05;
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
            kafkaTemplate.send(KafkaTopicConfig.TOPIC_VEHICLE_COMMAND_RESULTS, event.getVehicleId().toString(), resultPayload);
            log.info("Simulator [Vehicle {}] published result [{}] for command [{}] after {}ms",
                    event.getVehicleId(), resultStatus, event.getCommandId(), delayMs);

        } catch (Exception e) {
            log.error("Simulator error handling command message: {}", message, e);
        }
    }

    @KafkaListener(topics = KafkaTopicConfig.TOPIC_OTA_INSTRUCTIONS, groupId = "vehicle-simulator-group")
    public void handleIncomingOtaInstruction(String message) {
        try {
            OtaInstructionEvent instruction = objectMapper.readValue(message, OtaInstructionEvent.class);
            log.info("Simulator [Vehicle {}] starting OTA update for version [{}]",
                    instruction.getVehicleId(), instruction.getTargetVersionLabel());

            // 1. Downloading 25%
            publishOtaProgress(instruction, UpdateStatus.DOWNLOADING, 25, null);
            Thread.sleep(500);

            // 2. Downloading 100% -> Downloaded
            publishOtaProgress(instruction, UpdateStatus.DOWNLOADING, 100, null);
            publishOtaProgress(instruction, UpdateStatus.DOWNLOADED, 100, null);
            Thread.sleep(500);

            // 3. Installing 50%
            publishOtaProgress(instruction, UpdateStatus.INSTALLING, 50, null);
            Thread.sleep(500);

            // 4. 90% success, 10% failure simulation
            boolean success = random.nextDouble() > 0.10;

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
        kafkaTemplate.send(KafkaTopicConfig.TOPIC_OTA_UPDATE_STATUS, instruction.getVehicleId().toString(), payload);
    }
}
