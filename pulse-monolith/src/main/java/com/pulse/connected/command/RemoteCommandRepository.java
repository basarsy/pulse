package com.pulse.connected.command;

import com.pulse.connected.domain.CommandStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RemoteCommandRepository extends JpaRepository<RemoteCommand, UUID> {

    List<RemoteCommand> findAllByVehicleIdOrderByRequestedAtDesc(UUID vehicleId);

    /** Idempotency keys are scoped to the issuing user (unique constraint on issued_by, idempotency_key). */
    Optional<RemoteCommand> findByIssuedByIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    List<RemoteCommand> findAllByVehicleIdAndStatusIn(UUID vehicleId, List<CommandStatus> statuses);

    /** Commands in {@code status} requested before {@code cutoff} (queued commands that expired). */
    List<RemoteCommand> findAllByStatusAndRequestedAtBefore(CommandStatus status, Instant cutoff);

    /** Commands in one of {@code statuses} sent before {@code cutoff} (dispatched commands that timed out). */
    List<RemoteCommand> findAllByStatusInAndSentAtBefore(Collection<CommandStatus> statuses, Instant cutoff);
}
