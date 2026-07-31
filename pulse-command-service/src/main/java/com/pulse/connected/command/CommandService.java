package com.pulse.connected.command;

import com.pulse.connected.client.VehicleRegistryClient;
import com.pulse.connected.client.VehicleRegistryClient.VehicleInfo;
import com.pulse.connected.command.dto.CommandResponse;
import com.pulse.connected.command.dto.IssueCommandRequest;
import com.pulse.connected.common.exception.ResourceNotFoundException;
import com.pulse.connected.messaging.CommandKafkaProducer;
import com.pulse.connected.messaging.dto.VehicleCommandEvent;
import com.pulse.connected.domain.CommandStatus;
import com.pulse.connected.websocket.CommandWebSocketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.MeterRegistry;
import com.pulse.connected.common.AuditLogService;

@Service
public class CommandService {

    private static final Logger log = LoggerFactory.getLogger(CommandService.class);

    private final RemoteCommandRepository commandRepository;
    private final VehicleRegistryClient vehicleRegistryClient;
    private final CommandIdempotencyService idempotencyService;
    private final CommandKafkaProducer kafkaProducer;
    private final CommandWebSocketService webSocketService;
    private final AuditLogService auditLogService;
    private final MeterRegistry meterRegistry;

    public CommandService(
            RemoteCommandRepository commandRepository,
            VehicleRegistryClient vehicleRegistryClient,
            CommandIdempotencyService idempotencyService,
            CommandKafkaProducer kafkaProducer,
            CommandWebSocketService webSocketService,
            AuditLogService auditLogService,
            MeterRegistry meterRegistry) {
        this.commandRepository = commandRepository;
        this.vehicleRegistryClient = vehicleRegistryClient;
        this.idempotencyService = idempotencyService;
        this.kafkaProducer = kafkaProducer;
        this.webSocketService = webSocketService;
        this.auditLogService = auditLogService;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public CommandResponse issueCommand(UUID vehicleId, IssueCommandRequest request, UUID currentUserId) {
        if (!idempotencyService.checkRateLimit(currentUserId)) {
            throw new IllegalStateException("Command rate limit exceeded. Please wait before issuing more commands.");
        }

        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<String> existingCommandId = idempotencyService.getExistingCommandId(request.getIdempotencyKey());
            if (existingCommandId.isPresent()) {
                log.info("Idempotency hit for key [{}]. Returning existing command [{}]", request.getIdempotencyKey(), existingCommandId.get());
                return getCommandById(UUID.fromString(existingCommandId.get()));
            }
        }

        VehicleInfo vehicle = vehicleRegistryClient.getVehicle(vehicleId);
        if (vehicle == null) {
            throw new ResourceNotFoundException("Vehicle not found with ID: " + vehicleId);
        }

        RemoteCommand command = RemoteCommand.builder()
                .vehicleId(vehicleId)
                .issuedById(currentUserId)
                .type(request.getType())
                .status(CommandStatus.PENDING)
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        RemoteCommand saved = commandRepository.save(command);

        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            idempotencyService.storeIdempotencyKey(request.getIdempotencyKey(), saved.getId());
        }

        meterRegistry.counter("pulse.commands.issued", "type", request.getType().name()).increment();
        auditLogService.log(currentUserId, "COMMAND_ISSUED", "RemoteCommand", saved.getId().toString(), Map.of("type", request.getType().name(), "vehicleId", vehicleId.toString()));

        if ("ONLINE".equals(vehicle.connectivityState())) {
            dispatchToKafka(saved);
        } else {
            log.info("Vehicle [{}] is {}. Command [{}] queued in PENDING state.", 
                    vehicleId, vehicle.connectivityState(), saved.getId());
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

        if (resultStatus == CommandStatus.FAILED) {
            meterRegistry.counter("pulse.commands.failed").increment();
            auditLogService.log(command.getIssuedById(), "COMMAND_FAILED", "RemoteCommand", commandId.toString(), Map.of("resultMessage", resultMessage != null ? resultMessage : ""));
        } else if (resultStatus == CommandStatus.COMPLETED) {
            meterRegistry.counter("pulse.commands.completed").increment();
            auditLogService.log(command.getIssuedById(), "COMMAND_COMPLETED", "RemoteCommand", commandId.toString(), null);
        }

        CommandResponse response = mapToResponse(updated);
        webSocketService.notifyCommandStatusChange(command.getVehicleId(), response);
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
    public CommandResponse cancelCommand(UUID commandId, UUID currentUserId) {
        RemoteCommand command = commandRepository.findById(commandId)
                .orElseThrow(() -> new ResourceNotFoundException("Command not found with ID: " + commandId));

        if (command.getStatus() != CommandStatus.PENDING && command.getStatus() != CommandStatus.SENT) {
            throw new IllegalStateException("Cannot cancel command in status: " + command.getStatus());
        }

        command.setStatus(CommandStatus.FAILED);
        command.setResolvedAt(Instant.now());
        command.setResultMessage("Cancelled by user");

        RemoteCommand saved = commandRepository.save(command);
        auditLogService.log(currentUserId, "COMMAND_CANCELLED", "RemoteCommand", commandId.toString(), null);
        CommandResponse response = mapToResponse(saved);
        webSocketService.notifyCommandStatusChange(command.getVehicleId(), response);
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
            meterRegistry.counter("pulse.commands.timed_out").increment();
            auditLogService.log(command.getIssuedById(), "COMMAND_TIMED_OUT", "RemoteCommand", command.getId().toString(), null);
            webSocketService.notifyCommandStatusChange(command.getVehicleId(), mapToResponse(updated));
        }
    }

    private void dispatchToKafka(RemoteCommand command) {
        command.setStatus(CommandStatus.SENT);
        commandRepository.save(command);

        VehicleCommandEvent event = VehicleCommandEvent.builder()
                .commandId(command.getId())
                .vehicleId(command.getVehicleId())
                .type(command.getType())
                .issuedAt(command.getRequestedAt())
                .idempotencyKey(command.getIdempotencyKey())
                .build();

        kafkaProducer.dispatchCommand(event);
    }

    public CommandResponse mapToResponse(RemoteCommand command) {
        return CommandResponse.builder()
                .id(command.getId())
                .vehicleId(command.getVehicleId())
                .issuedByUserId(command.getIssuedById())
                .type(command.getType())
                .status(command.getStatus())
                .requestedAt(command.getRequestedAt())
                .resolvedAt(command.getResolvedAt())
                .resultMessage(command.getResultMessage())
                .idempotencyKey(command.getIdempotencyKey())
                .build();
    }
}
