package com.pulse.connected.command;

import com.pulse.connected.AbstractIntegrationTest;
import com.pulse.connected.auth.Role;
import com.pulse.connected.auth.User;
import com.pulse.connected.auth.UserRepository;
import com.pulse.connected.domain.CommandStatus;
import com.pulse.connected.domain.CommandType;
import com.pulse.connected.domain.ConnectivityState;
import com.pulse.connected.vehicle.Vehicle;
import com.pulse.connected.vehicle.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Commands queued for an offline vehicle are sent once it is online: by the reconnect flush, which
 * goes through the outbox so a failed flush is retried, and otherwise by the sweep.
 */
class QueuedCommandFlushTest extends AbstractIntegrationTest {

    @Autowired
    private PendingCommandSweep sweep;

    @Autowired
    private RemoteCommandRepository commandRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * The simulator's next heartbeat brings the vehicle online. The reconnect event is recorded in the
     * outbox, and its listener sends the queued command.
     */
    @Test
    void reconnect_SendsQueuedCommand_ThroughTheOutbox() {
        // Vehicle and command in one transaction, so the vehicle can't come online before the command exists.
        Queued queued = tx.execute(status -> queue(ConnectivityState.OFFLINE));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(commandRepository.findById(queued.commandId()).orElseThrow().getStatus())
                        .isNotEqualTo(CommandStatus.PENDING));

        // Completion is recorded just after the listener returns, so it can trail the command's update.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(jdbc.queryForList(
                                "SELECT completion_date FROM event_publication WHERE event_type LIKE ? AND serialized_event LIKE ?",
                                Instant.class, "%VehicleReconnectedEvent", "%" + queued.vehicleId() + "%"))
                        .as("the reconnect went through the outbox, and its listener completed")
                        .singleElement().isNotNull());
    }

    /**
     * The command missed the reconnect flush: the vehicle was already online when it was committed
     * (as when the command's transaction commits just after the flush read the queue).
     */
    @Test
    void sweep_SendsQueuedCommandOfOnlineVehicle() {
        Queued queued = tx.execute(status -> queue(ConnectivityState.ONLINE));
        assertThat(commandRepository.findById(queued.commandId()).orElseThrow().getStatus()).isEqualTo(CommandStatus.PENDING);

        sweep.sweep();

        RemoteCommand sent = commandRepository.findById(queued.commandId()).orElseThrow();
        assertThat(sent.getStatus()).isNotEqualTo(CommandStatus.PENDING);
        assertThat(sent.getSentAt()).isNotNull();
    }

    private record Queued(UUID vehicleId, UUID commandId) {
    }

    private Queued queue(ConnectivityState state) {
        User owner = userRepository.save(User.builder()
                .email(uniqueEmail("queued-commands"))
                .passwordHash("not-a-real-hash")
                .fullName("Queued Commands")
                .roles(Set.of(Role.OWNER))
                .build());
        String vin = ("QC" + UUID.randomUUID().toString().replace("-", "")).substring(0, 17).toUpperCase();
        Vehicle vehicle = vehicleRepository.save(Vehicle.builder()
                .vin(vin).model("QueueModel").modelYear(2026).owner(owner)
                .connectivityState(state).lastSeenAt(state == ConnectivityState.ONLINE ? Instant.now() : null)
                .currentSoftwareVersion("1.0.0")
                .build());
        RemoteCommand command = commandRepository.save(RemoteCommand.builder()
                .vehicle(vehicle).issuedBy(owner).type(CommandType.LOCK).status(CommandStatus.PENDING)
                .build());
        return new Queued(vehicle.getId(), command.getId());
    }
}
