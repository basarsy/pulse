package com.pulse.connected.vehicle;

import com.pulse.connected.AbstractIntegrationTest;
import com.pulse.connected.auth.Role;
import com.pulse.connected.auth.User;
import com.pulse.connected.auth.UserRepository;
import com.pulse.connected.domain.ConnectivityState;
import com.pulse.connected.messaging.dto.VehicleTelemetryEvent;
import com.pulse.connected.telemetry.TelemetryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Heartbeats, telemetry and OTA installs write the same vehicle row from different Kafka listener
 * threads. None of them may put back a column another one just changed.
 *
 * <p>The race is made deterministic with a row lock: another transaction locks the vehicle and sets
 * a new software version (as an install does) without committing. The heartbeat or telemetry write
 * then runs, reads the row (Postgres doesn't block reads, so it sees the old version) and waits on
 * the lock. When the install commits, the waiting write goes ahead. Saving a loaded copy of the
 * vehicle would write the old version back; a targeted UPDATE leaves it alone.
 */
class VehicleConcurrentWritesTest extends AbstractIntegrationTest {

    @Autowired
    private VehicleHeartbeats heartbeats;

    @Autowired
    private TelemetryService telemetryService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @Test
    void heartbeatDuringInstall_KeepsInstalledVersion() throws Exception {
        UUID vehicleId = vehicleOn("1.0.0");

        installWhile(vehicleId, "2.0.0", () -> heartbeats.record(vehicleId));

        Vehicle after = vehicleRepository.findById(vehicleId).orElseThrow();
        assertThat(after.getCurrentSoftwareVersion()).isEqualTo("2.0.0");
        assertThat(after.getConnectivityState()).isEqualTo(ConnectivityState.ONLINE);
    }

    @Test
    void telemetryDuringInstall_KeepsInstalledVersion() throws Exception {
        UUID vehicleId = vehicleOn("1.0.0");

        installWhile(vehicleId, "2.0.0", () -> telemetryService.ingestTelemetry(
                new VehicleTelemetryEvent(vehicleId, 42.0, 1234.0, 80.0, 48.1, 11.5, Instant.now())));

        Vehicle after = vehicleRepository.findById(vehicleId).orElseThrow();
        assertThat(after.getCurrentSoftwareVersion()).isEqualTo("2.0.0");
        assertThat(after.getBatteryOrFuelLevel()).isEqualTo(42.0);
        assertThat(after.getOdometerKm()).isEqualTo(1234.0);
    }

    /**
     * Runs {@code write} while an uncommitted install of {@code version} holds the vehicle's row lock,
     * and commits the install only once {@code write} is waiting for it.
     */
    private void installWhile(UUID vehicleId, String version, Runnable write) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> install = pool.submit(() -> tx.executeWithoutResult(status -> {
                jdbc.queryForList("SELECT id FROM vehicles WHERE id = ? FOR UPDATE", vehicleId);
                jdbc.update("UPDATE vehicles SET current_software_version = ? WHERE id = ?", version, vehicleId);
                locked.countDown();
                waitFor(commit);
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            int waitingBefore = lockWaiters();
            Future<?> concurrent = pool.submit(write);
            // The write has read the row and is blocked on the lock (simulator heartbeats may queue
            // there too, hence "one more than before").
            await().atMost(Duration.ofSeconds(10)).until(() -> lockWaiters() > waitingBefore);
            Thread.sleep(200);

            commit.countDown();
            install.get(10, TimeUnit.SECONDS);
            concurrent.get(10, TimeUnit.SECONDS);
        } finally {
            commit.countDown();
            pool.shutdownNow();
        }
    }

    private int lockWaiters() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE NOT granted", Integer.class);
        return count != null ? count : 0;
    }

    private static void waitFor(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private UUID vehicleOn(String softwareVersion) {
        User owner = userRepository.save(User.builder()
                .email(uniqueEmail("vehicle-writes"))
                .passwordHash("not-a-real-hash")
                .fullName("Vehicle Writes")
                .roles(Set.of(Role.OWNER))
                .build());
        String vin = ("VW" + UUID.randomUUID().toString().replace("-", "")).substring(0, 17).toUpperCase();
        return vehicleRepository.save(Vehicle.builder()
                .vin(vin).model("WritesModel").modelYear(2026).owner(owner)
                .connectivityState(ConnectivityState.OFFLINE).currentSoftwareVersion(softwareVersion)
                .build()).getId();
    }
}
