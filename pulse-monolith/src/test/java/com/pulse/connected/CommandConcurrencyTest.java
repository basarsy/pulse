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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A result, a cancel and the timeout sweep can all change the same command at the same time. Each
 * reads the command, checks its status and writes; without optimistic locking the last writer
 * wins and a final state can be overwritten (e.g. TIMED_OUT revived to COMPLETED).
 */
class CommandConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private RemoteCommandRepository commandRepository;

    @Autowired
    private TransactionTemplate tx;

    @Test
    void staleWriteAfterConcurrentFinalChange_IsRejected() {
        UUID commandId = tx.execute(status -> saveSentCommand().getId());

        // Writer A (e.g. the timeout sweep) reads the command while it is still SENT ...
        RemoteCommand staleCopy = tx.execute(status -> commandRepository.findById(commandId).orElseThrow());

        // ... writer B (the vehicle's result) completes it first ...
        tx.executeWithoutResult(status -> {
            RemoteCommand current = commandRepository.findById(commandId).orElseThrow();
            current.setStatus(CommandStatus.COMPLETED);
            commandRepository.save(current);
        });

        // ... so A's write, based on SENT, must fail instead of overwriting COMPLETED.
        staleCopy.setStatus(CommandStatus.TIMED_OUT);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> commandRepository.save(staleCopy)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(commandRepository.findById(commandId).orElseThrow().getStatus()).isEqualTo(CommandStatus.COMPLETED);
    }

    private RemoteCommand saveSentCommand() {
        User owner = userRepository.save(User.builder()
                .email(uniqueEmail("concurrency"))
                .passwordHash("not-a-real-hash")
                .fullName("Concurrency Test")
                .roles(Set.of(Role.OWNER))
                .build());
        String vin = ("WBC" + UUID.randomUUID().toString().replace("-", "")).substring(0, 17).toUpperCase();
        Vehicle vehicle = vehicleRepository.save(Vehicle.builder()
                .vin(vin)
                .model("BMW iX xDrive50")
                .modelYear(2026)
                .owner(owner)
                .connectivityState(ConnectivityState.OFFLINE)
                .currentSoftwareVersion("1.0.0")
                .build());
        return commandRepository.save(RemoteCommand.builder()
                .vehicle(vehicle)
                .issuedBy(owner)
                .type(CommandType.LOCK)
                .status(CommandStatus.SENT)
                .build());
    }
}
