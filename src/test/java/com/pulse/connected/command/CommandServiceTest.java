package com.pulse.connected.command;

import com.pulse.connected.auth.Role;
import com.pulse.connected.auth.User;
import com.pulse.connected.command.dto.CommandResponse;
import com.pulse.connected.command.dto.IssueCommandRequest;
import com.pulse.connected.messaging.CommandKafkaProducer;
import com.pulse.connected.vehicle.ConnectivityState;
import com.pulse.connected.vehicle.Vehicle;
import com.pulse.connected.vehicle.VehicleRepository;
import com.pulse.connected.websocket.CommandWebSocketService;
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
    private CommandKafkaProducer kafkaProducer;

    @Mock
    private CommandWebSocketService webSocketService;

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
        when(idempotencyService.getExistingCommandId("idem-key-1")).thenReturn(Optional.empty());
        when(vehicleRepository.findById(onlineVehicle.getId())).thenReturn(Optional.of(onlineVehicle));
        when(commandRepository.save(any(RemoteCommand.class))).thenReturn(pendingCommand);

        CommandResponse response = commandService.issueCommand(onlineVehicle.getId(), request, owner);

        assertThat(response).isNotNull();
        assertThat(response.getType()).isEqualTo(CommandType.LOCK);
        verify(kafkaProducer).dispatchCommand(any());
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
        verify(kafkaProducer, never()).dispatchCommand(any());
    }

    @Test
    void issueCommand_RateLimitExceeded_ThrowsException() {
        IssueCommandRequest request = IssueCommandRequest.builder()
                .type(CommandType.LOCK)
                .build();

        when(idempotencyService.checkRateLimit(owner.getId())).thenReturn(false);

        assertThatThrownBy(() -> commandService.issueCommand(onlineVehicle.getId(), request, owner))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rate limit exceeded");
    }

    @Test
    void cancelCommand_PendingCommand_Success() {
        UUID commandId = UUID.randomUUID();
        RemoteCommand command = RemoteCommand.builder()
                .id(commandId)
                .vehicle(onlineVehicle)
                .issuedBy(owner)
                .type(CommandType.REMOTE_START)
                .status(CommandStatus.PENDING)
                .build();

        when(commandRepository.findById(commandId)).thenReturn(Optional.of(command));
        when(commandRepository.save(command)).thenReturn(command);

        CommandResponse response = commandService.cancelCommand(commandId);

        assertThat(response.getStatus()).isEqualTo(CommandStatus.FAILED);
        assertThat(response.getResultMessage()).isEqualTo("Cancelled by user");
        verify(webSocketService).notifyCommandStatusChange(eq(onlineVehicle.getId()), any());
    }
}
