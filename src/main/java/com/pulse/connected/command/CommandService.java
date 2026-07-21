package com.pulse.connected.command;

import com.pulse.connected.auth.User;
import com.pulse.connected.command.dto.CommandResponse;
import com.pulse.connected.command.dto.IssueCommandRequest;
import com.pulse.connected.common.exception.ResourceNotFoundException;
import com.pulse.connected.messaging.CommandKafkaProducer;
import com.pulse.connected.messaging.dto.VehicleCommandEvent;
import com.pulse.connected.vehicle.ConnectivityState;
import com.pulse.connected.vehicle.Vehicle;
import com.pulse.connected.vehicle.VehicleRepository;
import com.pulse.connected.websocket.CommandWebSocketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CommandService {

    private static final Logger log = LoggerFactory.getLogger(CommandService.class);

    private final RemoteCommandRepository commandRepository;
    private final VehicleRepository vehicleRepository;
    private final CommandIdempotencyService idempotencyService;
    private final CommandKafkaProducer kafkaProducer;
    private final CommandWebSocketService webSocketService;

    public CommandService(
            RemoteCommandRepository commandRepository,
            VehicleRepository vehicleRepository,
            CommandIdempotencyService idempotencyService,
            CommandKafkaProducer kafkaProducer,
            CommandWebSocketService webSocketService) {
        this.commandRepository = commandRepository;
        this.vehicleRepository = vehicleRepository;
        this.idempotencyService = idempotencyService;
        this.kafkaProducer = kafkaProducer;
        this.webSocketService = webSocketService;
    }

    @Transactional
    public CommandResponse issueCommand(UUID vehicleId, IssueCommandRequest request, User currentUser) {
        if (!idempotencyService.checkRateLimit(currentUser.getId())) {
            throw new IllegalStateException("Command rate limit exceeded. Please wait before issuing more commands.");
        }

        // Idempotency check
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<String> existingCommandId = idempotencyService.getExistingCommandId(request.getIdempotencyKey());
            if (existingCommandId.isPresent()) {
                log.info("Idempotency hit for key [{}]. Returning existing command [{}]", request.getIdempotencyKey(), existingCommandId.get());
                return getCommandById(UUID.fromString(existingCommandId.get()));
            }
        }

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with ID: " + vehicleId));

        RemoteCommand command = RemoteCommand.builder()
                .vehicle(vehicle)
                .issuedBy(currentUser)
                .type(request.getType())
                .status(CommandStatus.PENDING)
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        RemoteCommand saved = commandRepository.save(command);

        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            idempotencyService.storeIdempotencyKey(request.getIdempotencyKey(), saved.getId());
        }

        // Dispatch immediately if vehicle is ONLINE
        if (vehicle.getConnectivityState() == ConnectivityState.ONLINE) {
            dispatchToKafka(saved);
        } else {
            log.info("Vehicle [{}] is {}. Command [{}] queued in PENDING state.", 
                    vehicle.getId(), vehicle.getConnectivityState(), saved.getId());
        }

        CommandResponse response = mapToResponse(saved);
        webSocketService.notifyCommandStatusChange(vehicleId, response);
        return response;
    }

    @Transactional
    public void processCommandResult(UUID commandId, CommandStatus resultStatus, String resultMessage) {
        Optional<RemoteCommand> commandOpt = commandRepository.findById(commandId);
        if (commandOpt.isEmpty()) {
            log.warn("Result received for non-existent command ID: {}", commandId);
            return;
        }

        RemoteCommand command = commandOpt.get();
        command.setStatus(resultStatus);
        command.setResolvedAt(Instant.now());
        command.setResultMessage(resultMessage);
        
        RemoteCommand updated = commandRepository.save(command);
        log.info("Updated command [{}] status to [{}] with message: {}", commandId, resultStatus, resultMessage);

        CommandResponse response = mapToResponse(updated);
        webSocketService.notifyCommandStatusChange(command.getVehicle().getId(), response);
    }

    @Transactional
    public void dispatchPendingCommandsForVehicle(UUID vehicleId) {
        List<RemoteCommand> pendingCommands = commandRepository.findAllByVehicleIdAndStatusIn(
                vehicleId, List.of(CommandStatus.PENDING));

        for (RemoteCommand command : pendingCommands) {
            log.info("Flushing pending command [{}] for reconnected vehicle [{}]", command.getId(), vehicleId);
            dispatchToKafka(command);
            CommandResponse response = mapToResponse(command);
            webSocketService.notifyCommandStatusChange(vehicleId, response);
        }
    }

    @Transactional
    public CommandResponse cancelCommand(UUID commandId) {
        RemoteCommand command = commandRepository.findById(commandId)
                .orElseThrow(() -> new ResourceNotFoundException("Command not found with ID: " + commandId));

        if (command.getStatus() != CommandStatus.PENDING && command.getStatus() != CommandStatus.SENT) {
            throw new IllegalStateException("Cannot cancel command in status: " + command.getStatus());
        }

        command.setStatus(CommandStatus.FAILED);
        command.setResolvedAt(Instant.now());
        command.setResultMessage("Cancelled by user");

        RemoteCommand saved = commandRepository.save(command);
        CommandResponse response = mapToResponse(saved);
        webSocketService.notifyCommandStatusChange(command.getVehicle().getId(), response);
        return response;
    }

    @Transactional(readOnly = true)
    public CommandResponse getCommandById(UUID commandId) {
        RemoteCommand command = commandRepository.findById(commandId)
                .orElseThrow(() -> new ResourceNotFoundException("Command not found with ID: " + commandId));
        return mapToResponse(command);
    }

    @Transactional(readOnly = true)
    public List<CommandResponse> getCommandHistory(UUID vehicleId) {
        return commandRepository.findAllByVehicleIdOrderByRequestedAtDesc(vehicleId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Scheduled(fixedRate = 30000)
    @Transactional
    public void checkStaleCommands() {
        Instant cutoff = Instant.now().minusSeconds(60);
        List<RemoteCommand> staleCommands = commandRepository.findStaleCommands(cutoff);

        for (RemoteCommand command : staleCommands) {
            log.warn("Command [{}] timed out after 60s without execution result.", command.getId());
            command.setStatus(CommandStatus.TIMED_OUT);
            command.setResolvedAt(Instant.now());
            command.setResultMessage("Command execution timed out after 60 seconds");
            
            RemoteCommand updated = commandRepository.save(command);
            webSocketService.notifyCommandStatusChange(command.getVehicle().getId(), mapToResponse(updated));
        }
    }

    private void dispatchToKafka(RemoteCommand command) {
        command.setStatus(CommandStatus.SENT);
        commandRepository.save(command);

        VehicleCommandEvent event = VehicleCommandEvent.builder()
                .commandId(command.getId())
                .vehicleId(command.getVehicle().getId())
                .type(command.getType())
                .issuedAt(command.getRequestedAt())
                .idempotencyKey(command.getIdempotencyKey())
                .build();

        kafkaProducer.dispatchCommand(event);
    }

    public CommandResponse mapToResponse(RemoteCommand command) {
        return CommandResponse.builder()
                .id(command.getId())
                .vehicleId(command.getVehicle().getId())
                .issuedByUserId(command.getIssuedBy().getId())
                .type(command.getType())
                .status(command.getStatus())
                .requestedAt(command.getRequestedAt())
                .resolvedAt(command.getResolvedAt())
                .resultMessage(command.getResultMessage())
                .idempotencyKey(command.getIdempotencyKey())
                .build();
    }
}
