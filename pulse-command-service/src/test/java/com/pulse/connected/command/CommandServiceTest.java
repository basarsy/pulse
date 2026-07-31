package com.pulse.connected.command;

import com.pulse.connected.domain.CommandType;
import com.pulse.connected.domain.CommandStatus;

import com.pulse.connected.client.VehicleRegistryClient;
import com.pulse.connected.client.VehicleRegistryClient.VehicleInfo;
import com.pulse.connected.command.dto.CommandResponse;
import com.pulse.connected.command.dto.IssueCommandRequest;
import com.pulse.connected.messaging.CommandKafkaProducer;
import com.pulse.connected.websocket.CommandWebSocketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
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
    private VehicleRegistryClient vehicleRegistryClient;

    @Mock
    private CommandIdempotencyService idempotencyService;

    @Mock
    private CommandKafkaProducer kafkaProducer;

    @Mock
    private CommandWebSocketService webSocketService;

    @Mock
    private com.pulse.connected.common.AuditLogService auditLogService;

    @org.mockito.Spy
    private io.micrometer.core.instrument.MeterRegistry meterRegistry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();

    @InjectMocks
    private CommandService commandService;

    private UUID ownerId;
    private UUID onlineVehicleId;
    private UUID offlineVehicleId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        onlineVehicleId = UUID.randomUUID();
        offlineVehicleId = UUID.randomUUID();
    }

    @Test
    void issueCommand_OnlineVehicle_DispatchesToKafka() {
        IssueCommandRequest request = IssueCommandRequest.builder()
                .type(CommandType.LOCK)
                .idempotencyKey("idem-key-1")
                .build();

        RemoteCommand pendingCommand = RemoteCommand.builder()
                .id(UUID.randomUUID())
                .vehicleId(onlineVehicleId)
                .issuedById(ownerId)
                .type(CommandType.LOCK)
                .status(CommandStatus.PENDING)
                .idempotencyKey("idem-key-1")
                .build();

        VehicleInfo onlineVehicleInfo = new VehicleInfo(onlineVehicleId, "WBA111", "X5", 2024, "ONLINE", "1.0.0", ownerId);

        when(idempotencyService.checkRateLimit(ownerId)).thenReturn(true);
        when(idempotencyService.getExistingCommandId("idem-key-1")).thenReturn(Optional.empty());
        when(vehicleRegistryClient.getVehicle(onlineVehicleId)).thenReturn(onlineVehicleInfo);
        when(commandRepository.save(any(RemoteCommand.class))).thenReturn(pendingCommand);

        CommandResponse response = commandService.issueCommand(onlineVehicleId, request, ownerId);

        assertThat(response).isNotNull();
        assertThat(response.getType()).isEqualTo(CommandType.LOCK);
        verify(kafkaProducer).dispatchCommand(any());
        verify(webSocketService).notifyCommandStatusChange(eq(onlineVehicleId), any());
    }

    @Test
    void issueCommand_OfflineVehicle_QueuesAsPending() {
        IssueCommandRequest request = IssueCommandRequest.builder()
                .type(CommandType.UNLOCK)
                .build();

        RemoteCommand pendingCommand = RemoteCommand.builder()
                .id(UUID.randomUUID())
                .vehicleId(offlineVehicleId)
                .issuedById(ownerId)
                .type(CommandType.UNLOCK)
                .status(CommandStatus.PENDING)
                .build();

        VehicleInfo offlineVehicleInfo = new VehicleInfo(offlineVehicleId, "WBA222", "X5", 2024, "OFFLINE", "1.0.0", ownerId);

        when(idempotencyService.checkRateLimit(ownerId)).thenReturn(true);
        when(vehicleRegistryClient.getVehicle(offlineVehicleId)).thenReturn(offlineVehicleInfo);
        when(commandRepository.save(any(RemoteCommand.class))).thenReturn(pendingCommand);

        CommandResponse response = commandService.issueCommand(offlineVehicleId, request, ownerId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(CommandStatus.PENDING);
        verify(kafkaProducer, never()).dispatchCommand(any());
    }

    @Test
    void issueCommand_RateLimitExceeded_ThrowsException() {
        IssueCommandRequest request = IssueCommandRequest.builder()
                .type(CommandType.LOCK)
                .build();

        when(idempotencyService.checkRateLimit(ownerId)).thenReturn(false);

        assertThatThrownBy(() -> commandService.issueCommand(onlineVehicleId, request, ownerId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rate limit exceeded");
    }

    @Test
    void cancelCommand_PendingCommand_Success() {
        UUID commandId = UUID.randomUUID();
        RemoteCommand command = RemoteCommand.builder()
                .id(commandId)
                .vehicleId(onlineVehicleId)
                .issuedById(ownerId)
                .type(CommandType.REMOTE_START)
                .status(CommandStatus.PENDING)
                .build();

        when(commandRepository.findById(commandId)).thenReturn(Optional.of(command));
        when(commandRepository.save(command)).thenReturn(command);

        CommandResponse response = commandService.cancelCommand(commandId, ownerId);

        assertThat(response.getStatus()).isEqualTo(CommandStatus.FAILED);
        assertThat(response.getResultMessage()).isEqualTo("Cancelled by user");
        verify(webSocketService).notifyCommandStatusChange(eq(onlineVehicleId), any());
    }
}
