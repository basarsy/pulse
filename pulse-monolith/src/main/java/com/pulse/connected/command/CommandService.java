package com.pulse.connected.command;

import com.pulse.connected.domain.CommandType;
import com.pulse.connected.domain.CommandStatus;

import com.pulse.connected.auth.User;
import com.pulse.connected.command.dto.CommandResponse;
import com.pulse.connected.command.dto.IssueCommandRequest;
import com.pulse.connected.common.exception.DuplicateResourceException;
import com.pulse.connected.common.exception.RateLimitExceededException;
import com.pulse.connected.common.exception.ResourceNotFoundException;
import com.pulse.connected.messaging.dto.VehicleCommandEvent;
import com.pulse.connected.domain.ConnectivityState;
import com.pulse.connected.vehicle.Vehicle;
import com.pulse.connected.vehicle.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
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
    private final VehicleRepository vehicleRepository;
    private final CommandRateLimiter rateLimiter;
    private final ApplicationEventPublisher eventPublisher;
    private final CommandWebSocketService webSocketService;
    private final AuditLogService auditLogService;
    private final MeterRegistry meterRegistry;
    private final CommandTimeoutProperties timeouts;

    public CommandService(
            RemoteCommandRepository commandRepository,
            VehicleRepository vehicleRepository,
            CommandRateLimiter rateLimiter,
            ApplicationEventPublisher eventPublisher,
            CommandWebSocketService webSocketService,
            AuditLogService auditLogService,
            MeterRegistry meterRegistry,
            CommandTimeoutProperties timeouts) {
        this.commandRepository = commandRepository;
        this.vehicleRepository = vehicleRepository;
        this.rateLimiter = rateLimiter;
        this.eventPublisher = eventPublisher;
        this.webSocketService = webSocketService;
        this.auditLogService = auditLogService;
        this.meterRegistry = meterRegistry;
        this.timeouts = timeouts;
    }

    @Transactional
    public CommandResponse issueCommand(UUID vehicleId, IssueCommandRequest request, User currentUser) {
        // A blank key means "no key"; storing it would make the second blank-key request collide.
        String idempotencyKey = request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()
                ? null : request.getIdempotencyKey();

        // Idempotency is decided by the database (unique on issued_by, idempotency_key); keys never
        // expire. A replay returns the original command and does not count against the rate limit.
        if (idempotencyKey != null) {
            Optional<RemoteCommand> existing =
                    commandRepository.findByIssuedByIdAndIdempotencyKey(currentUser.getId(), idempotencyKey);
            if (existing.isPresent()) {
                return replay(existing.get(), vehicleId, request);
            }
        }

        if (!rateLimiter.checkRateLimit(currentUser.getId())) {
            throw new RateLimitExceededException("Command rate limit exceeded. Please wait before issuing more commands.");
        }

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with ID: " + vehicleId));

        RemoteCommand command = RemoteCommand.builder()
                .vehicle(vehicle)
                .issuedBy(currentUser)
                .type(request.getType())
                .status(CommandStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .build();

        RemoteCommand saved = commandRepository.save(command);

        meterRegistry.counter("pulse.commands.issued", "type", request.getType().name()).increment();
        auditLogService.log(currentUser.getId(), "COMMAND_ISSUED", "RemoteCommand", saved.getId().toString(), Map.of("type", request.getType().name(), "vehicleId", vehicleId.toString()));

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

    /**
     * Same user and key: the same request repeated returns the original command, with no new side
     * effects. Reusing the key for a different vehicle or command type is a client error.
     */
    private CommandResponse replay(RemoteCommand existing, UUID vehicleId, IssueCommandRequest request) {
        if (!existing.getVehicle().getId().equals(vehicleId)) {
            throw new DuplicateResourceException("Idempotency key already used for a command on a different vehicle");
        }
        if (existing.getType() != request.getType()) {
            throw new DuplicateResourceException("Idempotency key already used for a " + existing.getType() + " command");
        }
        log.info("Idempotency hit for key [{}]. Returning existing command [{}]", existing.getIdempotencyKey(), existing.getId());
        return mapToResponse(existing);
    }

    @Transactional
    public void processCommandResult(UUID commandId, UUID vehicleId, CommandStatus resultStatus, String resultMessage) {
        Optional<RemoteCommand> commandOpt = commandRepository.findById(commandId);
        if (commandOpt.isEmpty()) {
            log.warn("Result received for non-existent command ID: {}", commandId);
            return;
        }

        RemoteCommand command = commandOpt.get();
        if (!command.getVehicle().getId().equals(vehicleId)) {
            log.warn("Ignoring result for command [{}] from vehicle [{}]: command belongs to vehicle [{}]",
                    commandId, vehicleId, command.getVehicle().getId());
            return;
        }
        if (!CommandStatusTransitions.isAllowed(command.getStatus(), resultStatus)) {
            // Late or redelivered result (e.g. after a timeout or cancellation). Not an error, so
            // it is not retried or dead-lettered. The status stays final, but the vehicle may have
            // really executed the command (an UNLOCK after a cancel), so it is audited.
            log.warn("Ignoring result for command [{}]: {} -> {} is not allowed",
                    commandId, command.getStatus(), resultStatus);
            meterRegistry.counter("pulse.commands.late_results").increment();
            auditLogService.log(command.getIssuedBy().getId(), "COMMAND_LATE_RESULT", "RemoteCommand",
                    commandId.toString(),
                    Map.of("reportedStatus", resultStatus.name(), "currentStatus", command.getStatus().name()));
            return;
        }

        command.setStatus(resultStatus);
        if (CommandStatusTransitions.isFinal(resultStatus)) {
            command.setResolvedAt(Instant.now());
        }
        command.setResultMessage(resultMessage);
        
        RemoteCommand updated = commandRepository.save(command);
        log.info("Updated command [{}] status to [{}] with message: {}", commandId, resultStatus, resultMessage);

        if (resultStatus == CommandStatus.FAILED) {
            meterRegistry.counter("pulse.commands.failed").increment();
            auditLogService.log(command.getIssuedBy().getId(), "COMMAND_FAILED", "RemoteCommand", commandId.toString(), Map.of("resultMessage", resultMessage != null ? resultMessage : ""));
        } else if (resultStatus == CommandStatus.COMPLETED) {
            meterRegistry.counter("pulse.commands.completed").increment();
            auditLogService.log(command.getIssuedBy().getId(), "COMMAND_COMPLETED", "RemoteCommand", commandId.toString(), null);
        }

        CommandResponse response = mapToResponse(updated);
        webSocketService.notifyCommandStatusChange(command.getVehicle().getId(), response);
    }

    @Transactional
    public void dispatchPendingCommandsForVehicle(UUID vehicleId) {
        List<RemoteCommand> pendingCommands = commandRepository.findAllByVehicleIdAndStatusIn(
                vehicleId, List.of(CommandStatus.PENDING));

        Instant queueCutoff = Instant.now().minus(timeouts.queueExpiry());
        for (RemoteCommand command : pendingCommands) {
            if (command.getRequestedAt() != null && command.getRequestedAt().isBefore(queueCutoff)) {
                // Expired but not yet swept: must never fire late.
                expire(command, queueExpiredMessage());
                continue;
            }
            log.info("Flushing pending command [{}] for reconnected vehicle [{}]", command.getId(), vehicleId);
            dispatchToKafka(command);
            CommandResponse response = mapToResponse(command);
            webSocketService.notifyCommandStatusChange(vehicleId, response);
        }
    }

    @Transactional
    public CommandResponse cancelCommand(UUID vehicleId, UUID commandId, User actor) {
        RemoteCommand command = findCommandForVehicle(vehicleId, commandId);

        if (command.getStatus() != CommandStatus.PENDING && command.getStatus() != CommandStatus.SENT) {
            throw new IllegalStateException("Cannot cancel command in status: " + command.getStatus());
        }

        command.setStatus(CommandStatus.CANCELLED);
        command.setResolvedAt(Instant.now());
        command.setResultMessage("Cancelled by user");

        RemoteCommand saved = commandRepository.save(command);
        meterRegistry.counter("pulse.commands.cancelled").increment();
        auditLogService.log(actor.getId(), "COMMAND_CANCELLED", "RemoteCommand", commandId.toString(), null);
        CommandResponse response = mapToResponse(saved);
        webSocketService.notifyCommandStatusChange(command.getVehicle().getId(), response);
        return response;
    }

    @Transactional(readOnly = true)
    public CommandResponse getCommandForVehicle(UUID vehicleId, UUID commandId) {
        return mapToResponse(findCommandForVehicle(vehicleId, commandId));
    }

    /**
     * Callers are authorised per vehicle, so a command is only visible through the vehicle it
     * belongs to. A mismatch is reported as "not found" to avoid leaking other commands' existence.
     */
    private RemoteCommand findCommandForVehicle(UUID vehicleId, UUID commandId) {
        RemoteCommand command = findCommand(commandId);
        if (!command.getVehicle().getId().equals(vehicleId)) {
            throw new ResourceNotFoundException("Command not found with ID: " + commandId);
        }
        return command;
    }

    private RemoteCommand findCommand(UUID commandId) {
        return commandRepository.findById(commandId)
                .orElseThrow(() -> new ResourceNotFoundException("Command not found with ID: " + commandId));
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
        Instant now = Instant.now();

        // Queued for a vehicle that never came online: expire rather than fire much later.
        String queueMessage = queueExpiredMessage();
        commandRepository.findAllByStatusAndRequestedAtBefore(CommandStatus.PENDING, now.minus(timeouts.queueExpiry()))
                .forEach(command -> expire(command, queueMessage));

        // Dispatched, but no final result in time. Counted from sending, not from the request, so a
        // command flushed after a long queue still gets the full execution window.
        String executionMessage = "Command execution timed out after " + timeouts.executionTimeout().toSeconds() + " seconds";
        commandRepository.findAllByStatusInAndSentAtBefore(
                        List.of(CommandStatus.SENT, CommandStatus.ACKNOWLEDGED), now.minus(timeouts.executionTimeout()))
                .forEach(command -> expire(command, executionMessage));
    }

    private String queueExpiredMessage() {
        return "Vehicle did not come online within " + timeouts.queueExpiry().toMinutes() + " minutes";
    }

    private void expire(RemoteCommand command, String message) {
        log.warn("Command [{}] in status {} timed out: {}", command.getId(), command.getStatus(), message);
        command.setStatus(CommandStatus.TIMED_OUT);
        command.setResolvedAt(Instant.now());
        command.setResultMessage(message);

        RemoteCommand updated = commandRepository.save(command);
        meterRegistry.counter("pulse.commands.timed_out").increment();
        auditLogService.log(command.getIssuedBy().getId(), "COMMAND_TIMED_OUT", "RemoteCommand", command.getId().toString(), null);
        webSocketService.notifyCommandStatusChange(command.getVehicle().getId(), mapToResponse(updated));
    }

    private void dispatchToKafka(RemoteCommand command) {
        command.setStatus(CommandStatus.SENT);
        command.setSentAt(Instant.now());
        commandRepository.save(command);

        VehicleCommandEvent event = VehicleCommandEvent.builder()
                .commandId(command.getId())
                .vehicleId(command.getVehicle().getId())
                .type(command.getType())
                .issuedAt(command.getRequestedAt())
                .idempotencyKey(command.getIdempotencyKey())
                .build();

        // Recorded in the outbox with this transaction; sent to Kafka after commit (CommandKafkaRelay).
        eventPublisher.publishEvent(event);
    }

    public CommandResponse mapToResponse(RemoteCommand command) {
        return CommandResponse.builder()
                .id(command.getId())
                .vehicleId(command.getVehicle().getId())
                .issuedByUserId(command.getIssuedBy().getId())
                .type(command.getType())
                .status(command.getStatus())
                .requestedAt(command.getRequestedAt())
                .sentAt(command.getSentAt())
                .resolvedAt(command.getResolvedAt())
                .resultMessage(command.getResultMessage())
                .idempotencyKey(command.getIdempotencyKey())
                .build();
    }
}
