package com.pulse.connected;

import com.pulse.connected.auth.Role;
import com.pulse.connected.auth.User;
import com.pulse.connected.auth.UserRepository;
import com.pulse.connected.command.CommandService;
import com.pulse.connected.command.RemoteCommandRepository;
import com.pulse.connected.command.dto.CommandResponse;
import com.pulse.connected.command.dto.IssueCommandRequest;
import com.pulse.connected.domain.CommandType;
import com.pulse.connected.domain.ConnectivityState;
import com.pulse.connected.vehicle.Vehicle;
import com.pulse.connected.vehicle.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Identical requests (same user, same key) arriving at the same moment must create exactly one
 * command, and every one of them must get it back. Not @Transactional: the requests have to commit
 * and race against each other for real.
 */
class IdempotencyRaceTest extends AbstractIntegrationTest {

    private static final int TWINS = 8;

    @Autowired
    private CommandService commandService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private RemoteCommandRepository commandRepository;

    @Test
    void concurrentTwins_CreateOneCommand() throws Exception {
        User owner = userRepository.save(User.builder()
                .email(uniqueEmail("race"))
                .passwordHash("not-a-real-hash")
                .fullName("Race Test")
                .roles(Set.of(Role.OWNER))
                .build());
        String vin = ("WBR" + UUID.randomUUID().toString().replace("-", "")).substring(0, 17).toUpperCase();
        Vehicle vehicle = vehicleRepository.save(Vehicle.builder()
                .vin(vin)
                .model("BMW iX xDrive50")
                .modelYear(2026)
                .owner(owner)
                .connectivityState(ConnectivityState.OFFLINE)
                .currentSoftwareVersion("1.0.0")
                .build());
        String key = "race-" + UUID.randomUUID();
        IssueCommandRequest request = IssueCommandRequest.builder().type(CommandType.LOCK).idempotencyKey(key).build();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(TWINS);
        List<Future<CommandResponse>> results = new ArrayList<>();
        try {
            for (int i = 0; i < TWINS; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return commandService.issueCommand(vehicle.getId(), request, owner);
                }));
            }
            start.countDown();

            List<UUID> ids = new ArrayList<>();
            for (Future<CommandResponse> result : results) {
                ids.add(result.get(30, TimeUnit.SECONDS).getId()); // rethrows any request's failure
            }

            assertThat(ids).hasSize(TWINS).containsOnly(ids.get(0));
            assertThat(commandRepository.findByIssuedByIdAndIdempotencyKey(owner.getId(), key)).isPresent();
            assertThat(commandRepository.findAllByVehicleIdOrderByRequestedAtDesc(vehicle.getId())).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
