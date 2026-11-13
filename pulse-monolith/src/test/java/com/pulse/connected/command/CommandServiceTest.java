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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private CommandIdempotencyService idempotencyService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private CommandWebSocketService webSocketService;

    @Mock
    private com.pulse.connected.common.AuditLogService auditLogService;

    @org.mockito.Spy
    private io.micrometer.core.instrument.MeterRegistry meterRegistry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();

    @InjectMocks
    private CommandService commandService;

    private User owner;
    private Vehicle onlineVehicle;
    private Vehicle offlineVehicle;

    @BeforeEach
    void setUp() {
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

        when(idempotencyService.checkRateLimit(owner.getId())).thenReturn(true);
        when(idempotencyService.getExistingCommandId(owner.getId(), "idem-key-1")).thenReturn(Optional.empty());
        when(vehicleRepository.findById(onlineVehicle.getId())).thenReturn(Optional.of(onlineVehicle));
        when(commandRepository.save(any(RemoteCommand.class))).thenReturn(pendingCommand);

        CommandResponse response = commandService.issueCommand(onlineVehicle.getId(), request, owner);

        assertThat(response).isNotNull();
        assertThat(response.getType()).isEqualTo(CommandType.LOCK);
        verify(eventPublisher).publishEvent(any(VehicleCommandEvent.class));
        verify(webSocketService).notifyCommandStatusChange(eq(onlineVehicle.getId()), any());
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

        when(idempotencyService.checkRateLimit(owner.getId())).thenReturn(true);
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

        when(idempotencyService.checkRateLimit(owner.getId())).thenReturn(false);

        assertThatThrownBy(() -> commandService.issueCommand(onlineVehicle.getId(), request, owner))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("rate limit exceeded");
    }

    @Test
    void issueCommand_IdempotencyKeyReusedForAnotherVehicle_Conflict() {
        RemoteCommand earlier = pendingCommandFor(offlineVehicle);
        IssueCommandRequest request = IssueCommandRequest.builder()
                .type(CommandType.LOCK)
                .idempotencyKey("idem-key-1")
                .build();

        when(idempotencyService.checkRateLimit(owner.getId())).thenReturn(true);
        when(idempotencyService.getExistingCommandId(owner.getId(), "idem-key-1"))
                .thenReturn(Optional.of(earlier.getId().toString()));
        when(commandRepository.findById(earlier.getId())).thenReturn(Optional.of(earlier));

        assertThatThrownBy(() -> commandService.issueCommand(onlineVehicle.getId(), request, owner))
                .isInstanceOf(DuplicateResourceException.class);
        verify(commandRepository, never()).save(any());
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

        assertThat(response.getStatus()).isEqualTo(CommandStatus.FAILED);
        assertThat(response.getResultMessage()).isEqualTo("Cancelled by user");
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

    private RemoteCommand pendingCommandFor(Vehicle vehicle) {
        return RemoteCommand.builder()
                .id(UUID.randomUUID())
                .vehicle(vehicle)
                .issuedBy(owner)
                .type(CommandType.REMOTE_START)
                .status(CommandStatus.PENDING)
                .build();
    }
}
