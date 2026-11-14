package com.pulse.connected.command;

import com.pulse.connected.domain.CommandType;
import com.pulse.connected.domain.CommandStatus;

import com.pulse.connected.auth.Role;
import com.pulse.connected.auth.User;
import com.pulse.connected.command.dto.CommandResponse;
import com.pulse.connected.command.dto.IssueCommandRequest;
import com.pulse.connected.common.exception.DuplicateResourceException;
import com.pulse.connected.common.exception.RateLimitExceededException;
import com.pulse.connected.common.exception.ResourceNotFoundException;
import com.pulse.connected.domain.ConnectivityState;
import com.pulse.connected.messaging.dto.VehicleCommandEvent;
import com.pulse.connected.vehicle.Vehicle;
import org.springframework.context.ApplicationEventPublisher;
import com.pulse.connected.vehicle.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommandServiceTest {

    @Mock
    private RemoteCommandRepository commandRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private CommandRateLimiter rateLimiter;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private CommandWebSocketService webSocketService;

    @Mock
    private com.pulse.connected.common.AuditLogService auditLogService;

    @org.mockito.Spy
    private io.micrometer.core.instrument.MeterRegistry meterRegistry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();

    private CommandService commandService;

    private User owner;
    private Vehicle onlineVehicle;
    private Vehicle offlineVehicle;

    @BeforeEach
    void setUp() {
        commandService = new CommandService(commandRepository, vehicleRepository, rateLimiter,
                eventPublisher, webSocketService, auditLogService, meterRegistry,
                new CommandTimeoutProperties(Duration.ofMinutes(15), Duration.ofSeconds(60)));

        owner = User.builder()
                .id(UUID.randomUUID())
                .email("owner@example.com")
                .fullName("Owner Name")
                .roles(Set.of(Role.OWNER))
                .build();

        onlineVehicle = Vehicle.builder()
                .id(UUID.randomUUID())
                .vin("WBA11111111111111")
                .model("BMW iX xDrive50")
                .modelYear(2026)
                .owner(owner)
                .connectivityState(ConnectivityState.ONLINE)
                .build();

        offlineVehicle = Vehicle.builder()
                .id(UUID.randomUUID())
                .vin("WBA22222222222222")
                .model("BMW i4 eDrive40")
                .modelYear(2026)
                .owner(owner)
                .connectivityState(ConnectivityState.OFFLINE)
                .build();
    }

    @Test
    void issueCommand_OnlineVehicle_DispatchesToKafka() {
        IssueCommandRequest request = IssueCommandRequest.builder()
                .type(CommandType.LOCK)
                .idempotencyKey("idem-key-1")
                .build();

        RemoteCommand pendingCommand = RemoteCommand.builder()
                .id(UUID.randomUUID())
                .vehicle(onlineVehicle)
                .issuedBy(owner)
                .type(CommandType.LOCK)
                .status(CommandStatus.PENDING)
                .idempotencyKey("idem-key-1")
                .build();

        when(rateLimiter.checkRateLimit(owner.getId())).thenReturn(true);
        when(commandRepository.findByIssuedByIdAndIdempotencyKey(owner.getId(), "idem-key-1")).thenReturn(Optional.empty());
        when(vehicleRepository.findById(onlineVehicle.getId())).thenReturn(Optional.of(onlineVehicle));
        when(commandRepository.save(any(RemoteCommand.class))).thenReturn(pendingCommand);

        CommandResponse response = commandService.issueCommand(onlineVehicle.getId(), request, owner);

        assertThat(response).isNotNull();
        assertThat(response.getType()).isEqualTo(CommandType.LOCK);
        verify(eventPublisher).publishEvent(any(VehicleCommandEvent.class));
        verify(webSocketService).notifyCommandStatusChange(eq(onlineVehicle.getId()), any());
        assertThat(pendingCommand.getStatus()).isEqualTo(CommandStatus.SENT);
        assertThat(pendingCommand.getSentAt()).isNotNull();
    }

    @Test
    void checkStaleCommands_ExpiresQueuedAfterQueueExpiry() {
        RemoteCommand queued = pendingCommandFor(offlineVehicle);
        when(commandRepository.findAllByStatusAndRequestedAtBefore(eq(CommandStatus.PENDING), any()))
                .thenReturn(List.of(queued));
        when(commandRepository.save(queued)).thenReturn(queued);
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);

        Instant before = Instant.now();
        commandService.checkStaleCommands();
        Instant after = Instant.now();

        verify(commandRepository).findAllByStatusAndRequestedAtBefore(eq(CommandStatus.PENDING), cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before.minus(Duration.ofMinutes(15)), after.minus(Duration.ofMinutes(15)));
        assertThat(queued.getStatus()).isEqualTo(CommandStatus.TIMED_OUT);
        assertThat(queued.getResultMessage()).isEqualTo("Vehicle did not come online within 15 minutes");
        assertThat(queued.getResolvedAt()).isNotNull();
    }

    @Test
    void checkStaleCommands_TimesOutDispatchedAfterExecutionTimeout() {
        RemoteCommand acknowledged = commandFor(onlineVehicle, CommandStatus.ACKNOWLEDGED);
        when(commandRepository.findAllByStatusInAndSentAtBefore(
                eq(List.of(CommandStatus.SENT, CommandStatus.ACKNOWLEDGED)), any()))
                .thenReturn(List.of(acknowledged));
        when(commandRepository.save(acknowledged)).thenReturn(acknowledged);
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);

        Instant before = Instant.now();
        commandService.checkStaleCommands();
        Instant after = Instant.now();

        verify(commandRepository).findAllByStatusInAndSentAtBefore(any(), cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before.minus(Duration.ofSeconds(60)), after.minus(Duration.ofSeconds(60)));
        assertThat(acknowledged.getStatus()).isEqualTo(CommandStatus.TIMED_OUT);
        assertThat(acknowledged.getResultMessage()).isEqualTo("Command execution timed out after 60 seconds");
        assertThat(meterRegistry.counter("pulse.commands.timed_out").count()).isEqualTo(1.0);
    }

    @Test
    void issueCommand_OfflineVehicle_QueuesAsPending() {
        IssueCommandRequest request = IssueCommandRequest.builder()
                .type(CommandType.UNLOCK)
                .build();

        RemoteCommand pendingCommand = RemoteCommand.builder()
                .id(UUID.randomUUID())
                .vehicle(offlineVehicle)
                .issuedBy(owner)
                .type(CommandType.UNLOCK)
                .status(CommandStatus.PENDING)
                .build();

        when(rateLimiter.checkRateLimit(owner.getId())).thenReturn(true);
        when(vehicleRepository.findById(offlineVehicle.getId())).thenReturn(Optional.of(offlineVehicle));
        when(commandRepository.save(any(RemoteCommand.class))).thenReturn(pendingCommand);

        CommandResponse response = commandService.issueCommand(offlineVehicle.getId(), request, owner);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(CommandStatus.PENDING);
        verify(eventPublisher, never()).publishEvent(any(VehicleCommandEvent.class));
    }

    @Test
    void issueCommand_RateLimitExceeded_ThrowsException() {
        IssueCommandRequest request = IssueCommandRequest.builder()
                .type(CommandType.LOCK)
                .build();

        when(rateLimiter.checkRateLimit(owner.getId())).thenReturn(false);

        assertThatThrownBy(() -> commandService.issueCommand(onlineVehicle.getId(), request, owner))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("rate limit exceeded");
    }

    @Test
    void issueCommand_IdempotencyKeyReusedForAnotherVehicle_Conflict() {
        RemoteCommand earlier = keyedCommand(offlineVehicle, CommandType.LOCK, CommandStatus.PENDING, "idem-key-1");
        when(commandRepository.findByIssuedByIdAndIdempotencyKey(owner.getId(), "idem-key-1"))
                .thenReturn(Optional.of(earlier));

        assertThatThrownBy(() -> commandService.issueCommand(onlineVehicle.getId(), keyedRequest(CommandType.LOCK, "idem-key-1"), owner))
                .isInstanceOf(DuplicateResourceException.class);
        verify(commandRepository, never()).save(any());
    }

    @Test
    void issueCommand_KnownKey_ReturnsOriginalWithoutSideEffects() {
        RemoteCommand original = keyedCommand(onlineVehicle, CommandType.LOCK, CommandStatus.COMPLETED, "k");
        when(commandRepository.findByIssuedByIdAndIdempotencyKey(owner.getId(), "k")).thenReturn(Optional.of(original));

        CommandResponse response = commandService.issueCommand(onlineVehicle.getId(), keyedRequest(CommandType.LOCK, "k"), owner);

        assertThat(response.getId()).isEqualTo(original.getId());
        assertThat(response.getStatus()).isEqualTo(CommandStatus.COMPLETED);
        verify(commandRepository, never()).save(any());
        verifyNoInteractions(eventPublisher, webSocketService, auditLogService, rateLimiter);
    }

    @Test
    void issueCommand_KnownKeyWhileRateLimited_StillReturnsOriginal() {
        // No rateLimiter stub: a replay must not consult (or consume) the rate limit at all.
        RemoteCommand original = keyedCommand(onlineVehicle, CommandType.LOCK, CommandStatus.SENT, "k");
        when(commandRepository.findByIssuedByIdAndIdempotencyKey(owner.getId(), "k")).thenReturn(Optional.of(original));

        CommandResponse response = commandService.issueCommand(onlineVehicle.getId(), keyedRequest(CommandType.LOCK, "k"), owner);

        assertThat(response.getId()).isEqualTo(original.getId());
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void issueCommand_KnownKeyDifferentType_Conflict() {
        RemoteCommand original = keyedCommand(onlineVehicle, CommandType.LOCK, CommandStatus.SENT, "k");
        when(commandRepository.findByIssuedByIdAndIdempotencyKey(owner.getId(), "k")).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> commandService.issueCommand(onlineVehicle.getId(), keyedRequest(CommandType.UNLOCK, "k"), owner))
                .isInstanceOf(DuplicateResourceException.class);
        verify(commandRepository, never()).save(any());
    }

    @Test
    void issueCommand_BlankKey_StoredAsNoKey() {
        when(rateLimiter.checkRateLimit(owner.getId())).thenReturn(true);
        when(vehicleRepository.findById(offlineVehicle.getId())).thenReturn(Optional.of(offlineVehicle));
        when(commandRepository.save(any(RemoteCommand.class))).thenAnswer(invocation -> {
            RemoteCommand saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        ArgumentCaptor<RemoteCommand> saved = ArgumentCaptor.forClass(RemoteCommand.class);

        commandService.issueCommand(offlineVehicle.getId(), keyedRequest(CommandType.LOCK, "  "), owner);

        verify(commandRepository).save(saved.capture());
        assertThat(saved.getValue().getIdempotencyKey()).isNull();
        verify(commandRepository, never()).findByIssuedByIdAndIdempotencyKey(any(), any());
    }

    private IssueCommandRequest keyedRequest(CommandType type, String key) {
        return IssueCommandRequest.builder().type(type).idempotencyKey(key).build();
    }

    private RemoteCommand keyedCommand(Vehicle vehicle, CommandType type, CommandStatus status, String key) {
        return RemoteCommand.builder()
                .id(UUID.randomUUID())
                .vehicle(vehicle)
                .issuedBy(owner)
                .type(type)
                .status(status)
                .idempotencyKey(key)
                .build();
    }

    @Test
    void getCommandForVehicle_CommandBelongsToAnotherVehicle_NotFound() {
        RemoteCommand command = pendingCommandFor(offlineVehicle);
        when(commandRepository.findById(command.getId())).thenReturn(Optional.of(command));

        assertThatThrownBy(() -> commandService.getCommandForVehicle(onlineVehicle.getId(), command.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cancelCommand_PendingCommand_SuccessAndAuditsActingUser() {
        User familyMember = User.builder()
                .id(UUID.randomUUID())
                .email("family@example.com")
                .fullName("Family Member")
                .roles(Set.of(Role.FAMILY_MEMBER))
                .build();
        RemoteCommand command = pendingCommandFor(onlineVehicle);

        when(commandRepository.findById(command.getId())).thenReturn(Optional.of(command));
        when(commandRepository.save(command)).thenReturn(command);

        CommandResponse response = commandService.cancelCommand(onlineVehicle.getId(), command.getId(), familyMember);

        assertThat(response.getStatus()).isEqualTo(CommandStatus.CANCELLED);
        assertThat(response.getResultMessage()).isEqualTo("Cancelled by user");
        assertThat(meterRegistry.counter("pulse.commands.cancelled").count()).isEqualTo(1.0);
        verify(webSocketService).notifyCommandStatusChange(eq(onlineVehicle.getId()), any());
        verify(auditLogService).log(eq(familyMember.getId()), eq("COMMAND_CANCELLED"), any(), any(), any());
    }

    @Test
    void cancelCommand_CommandBelongsToAnotherVehicle_NotFound() {
        RemoteCommand command = pendingCommandFor(offlineVehicle);
        when(commandRepository.findById(command.getId())).thenReturn(Optional.of(command));

        assertThatThrownBy(() -> commandService.cancelCommand(onlineVehicle.getId(), command.getId(), owner))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(commandRepository, never()).save(any());
    }

    @Test
    void processCommandResult_AfterTimeout_IsIgnored() {
        assertLateResultIgnored(CommandStatus.TIMED_OUT);
    }

    @Test
    void processCommandResult_AfterCancel_IsIgnored() {
        assertLateResultIgnored(CommandStatus.CANCELLED);
    }

    private void assertLateResultIgnored(CommandStatus finalStatus) {
        RemoteCommand command = commandFor(onlineVehicle, finalStatus);
        when(commandRepository.findById(command.getId())).thenReturn(Optional.of(command));

        commandService.processCommandResult(command.getId(), onlineVehicle.getId(), CommandStatus.COMPLETED, "done");

        assertThat(command.getStatus()).isEqualTo(finalStatus);
        verify(commandRepository, never()).save(any());
        verifyNoInteractions(webSocketService);
        // The vehicle may really have executed it (e.g. unlocked after a cancel): keep a record.
        verify(auditLogService).log(eq(owner.getId()), eq("COMMAND_LATE_RESULT"), eq("RemoteCommand"),
                eq(command.getId().toString()),
                eq(java.util.Map.of("reportedStatus", "COMPLETED", "currentStatus", finalStatus.name())));
        assertThat(meterRegistry.counter("pulse.commands.late_results").count()).isEqualTo(1.0);
    }

    @Test
    void dispatchPendingCommands_ExpiresInsteadOfSendingCommandPastQueueExpiry() {
        RemoteCommand stale = pendingCommandFor(onlineVehicle);
        stale.setRequestedAt(Instant.now().minus(Duration.ofMinutes(16)));
        RemoteCommand fresh = pendingCommandFor(onlineVehicle);
        fresh.setRequestedAt(Instant.now().minus(Duration.ofMinutes(1)));
        when(commandRepository.findAllByVehicleIdAndStatusIn(onlineVehicle.getId(), List.of(CommandStatus.PENDING)))
                .thenReturn(List.of(stale, fresh));
        when(commandRepository.save(any(RemoteCommand.class))).thenAnswer(invocation -> invocation.getArgument(0));

        commandService.dispatchPendingCommandsForVehicle(onlineVehicle.getId());

        assertThat(stale.getStatus()).isEqualTo(CommandStatus.TIMED_OUT);
        assertThat(stale.getResultMessage()).isEqualTo("Vehicle did not come online within 15 minutes");
        assertThat(fresh.getStatus()).isEqualTo(CommandStatus.SENT);
        ArgumentCaptor<VehicleCommandEvent> sent = ArgumentCaptor.forClass(VehicleCommandEvent.class);
        verify(eventPublisher).publishEvent(sent.capture());
        assertThat(sent.getValue().getCommandId()).isEqualTo(fresh.getId());
    }

    @Test
    void processCommandResult_FromAnotherVehicle_IsIgnored() {
        RemoteCommand command = commandFor(onlineVehicle, CommandStatus.SENT);
        when(commandRepository.findById(command.getId())).thenReturn(Optional.of(command));

        commandService.processCommandResult(command.getId(), offlineVehicle.getId(), CommandStatus.COMPLETED, "done");

        assertThat(command.getStatus()).isEqualTo(CommandStatus.SENT);
        verify(commandRepository, never()).save(any());
        verifyNoInteractions(webSocketService);
    }

    @Test
    void processCommandResult_Acknowledged_DoesNotResolve() {
        RemoteCommand command = commandFor(onlineVehicle, CommandStatus.SENT);
        when(commandRepository.findById(command.getId())).thenReturn(Optional.of(command));
        when(commandRepository.save(command)).thenReturn(command);

        commandService.processCommandResult(command.getId(), onlineVehicle.getId(), CommandStatus.ACKNOWLEDGED, null);

        assertThat(command.getStatus()).isEqualTo(CommandStatus.ACKNOWLEDGED);
        assertThat(command.getResolvedAt()).isNull();
        verify(commandRepository).save(command);
    }

    @Test
    void processCommandResult_Completed_Resolves() {
        RemoteCommand command = commandFor(onlineVehicle, CommandStatus.ACKNOWLEDGED);
        when(commandRepository.findById(command.getId())).thenReturn(Optional.of(command));
        when(commandRepository.save(command)).thenReturn(command);

        commandService.processCommandResult(command.getId(), onlineVehicle.getId(), CommandStatus.COMPLETED, "done");

        assertThat(command.getStatus()).isEqualTo(CommandStatus.COMPLETED);
        assertThat(command.getResolvedAt()).isNotNull();
        assertThat(meterRegistry.counter("pulse.commands.completed").count()).isEqualTo(1.0);
    }

    private RemoteCommand pendingCommandFor(Vehicle vehicle) {
        return commandFor(vehicle, CommandStatus.PENDING);
    }

    private RemoteCommand commandFor(Vehicle vehicle, CommandStatus status) {
        return RemoteCommand.builder()
                .id(UUID.randomUUID())
                .vehicle(vehicle)
                .issuedBy(owner)
                .type(CommandType.REMOTE_START)
                .status(status)
                .build();
    }
}
