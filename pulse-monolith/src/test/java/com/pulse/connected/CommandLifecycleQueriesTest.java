package com.pulse.connected;

import com.pulse.connected.auth.Role;
import com.pulse.connected.auth.User;
import com.pulse.connected.auth.UserRepository;
import com.pulse.connected.command.RemoteCommand;
import com.pulse.connected.command.RemoteCommandRepository;
import com.pulse.connected.domain.CommandStatus;
import com.pulse.connected.domain.CommandType;
import com.pulse.connected.domain.ConnectivityState;
import com.pulse.connected.vehicle.Vehicle;
import com.pulse.connected.vehicle.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the semantics of the lifecycle queries against real Postgres. Each test runs in a
 * transaction that is rolled back, so the simulator never sees (or heartbeats) these rows.
 */
@Transactional
class CommandLifecycleQueriesTest extends AbstractIntegrationTest {

    private static final List<CommandStatus> DISPATCHED = List.of(CommandStatus.SENT, CommandStatus.ACKNOWLEDGED);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private RemoteCommandRepository commandRepository;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(User.builder()
                .email(uniqueEmail("lifecycle"))
                .passwordHash("not-a-real-hash")
                .fullName("Lifecycle Test")
                .roles(Set.of(Role.OWNER))
                .build());
    }

    @Test
    void pendingCommand_FoundByRequestedAtCutoff() {
        RemoteCommand command = saveCommand(CommandStatus.PENDING, null);
        Instant now = Instant.now();

        assertThat(ids(commandRepository.findAllByStatusAndRequestedAtBefore(CommandStatus.PENDING, now.plus(Duration.ofHours(1)))))
                .contains(command.getId());
        assertThat(ids(commandRepository.findAllByStatusAndRequestedAtBefore(CommandStatus.PENDING, now.minus(Duration.ofHours(1)))))
                .doesNotContain(command.getId());
    }

    @Test
    void flushedCommand_UsesSentAtNotRequestedAt() {
        // requested_at is "now" (@CreationTimestamp); sent_at is an hour later. A cutoff between the
        // two only excludes the command if the query really looks at sent_at.
        Instant now = Instant.now();
        RemoteCommand command = saveCommand(CommandStatus.SENT, now.plus(Duration.ofHours(1)));

        assertThat(ids(commandRepository.findAllByStatusInAndSentAtBefore(DISPATCHED, now.plus(Duration.ofMinutes(30)))))
                .doesNotContain(command.getId());
        assertThat(ids(commandRepository.findAllByStatusInAndSentAtBefore(DISPATCHED, now.plus(Duration.ofHours(2)))))
                .contains(command.getId());
    }

    // A fixed cutoff far in the past only ever matches rows these tests create: ONLINE vehicles
    // always have last_seen_at set, because a heartbeat sets both.
    private static final Instant OLD_CUTOFF = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant LONG_AGO = Instant.parse("1999-01-01T00:00:00Z");

    @Test
    void silentOnlineVehicle_MarkedOffline() {
        Vehicle vehicle = saveVehicle(ConnectivityState.ONLINE, LONG_AGO);

        int marked = vehicleRepository.markOnlineVehiclesOfflineLastSeenBefore(OLD_CUTOFF);

        assertThat(marked).isEqualTo(1);
        assertThat(reload(vehicle).getConnectivityState()).isEqualTo(ConnectivityState.OFFLINE);
    }

    @Test
    void recentlySeenVehicle_StaysOnline() {
        Vehicle vehicle = saveVehicle(ConnectivityState.ONLINE, Instant.now());

        vehicleRepository.markOnlineVehiclesOfflineLastSeenBefore(OLD_CUTOFF);

        assertThat(reload(vehicle).getConnectivityState()).isEqualTo(ConnectivityState.ONLINE);
    }

    @Test
    void sleepingVehicle_Untouched() {
        Vehicle vehicle = saveVehicle(ConnectivityState.SLEEPING, LONG_AGO);

        vehicleRepository.markOnlineVehiclesOfflineLastSeenBefore(OLD_CUTOFF);

        assertThat(reload(vehicle).getConnectivityState()).isEqualTo(ConnectivityState.SLEEPING);
    }

    private Vehicle reload(Vehicle vehicle) {
        return vehicleRepository.findById(vehicle.getId()).orElseThrow();
    }

    private RemoteCommand saveCommand(CommandStatus status, Instant sentAt) {
        return commandRepository.saveAndFlush(RemoteCommand.builder()
                .vehicle(saveVehicle(ConnectivityState.OFFLINE, null))
                .issuedBy(owner)
                .type(CommandType.LOCK)
                .status(status)
                .sentAt(sentAt)
                .build());
    }

    private Vehicle saveVehicle(ConnectivityState state, Instant lastSeenAt) {
        String vin = ("WBA" + UUID.randomUUID().toString().replace("-", "")).substring(0, 17).toUpperCase();
        return vehicleRepository.saveAndFlush(Vehicle.builder()
                .vin(vin)
                .model("BMW iX xDrive50")
                .modelYear(2026)
                .owner(owner)
                .connectivityState(state)
                .currentSoftwareVersion("1.0.0")
                .lastSeenAt(lastSeenAt)
                .build());
    }

    private static List<UUID> ids(List<RemoteCommand> commands) {
        return commands.stream().map(RemoteCommand::getId).toList();
    }
}
