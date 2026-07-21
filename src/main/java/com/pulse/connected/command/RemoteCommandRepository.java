package com.pulse.connected.command;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RemoteCommandRepository extends JpaRepository<RemoteCommand, UUID> {

    List<RemoteCommand> findAllByVehicleIdOrderByRequestedAtDesc(UUID vehicleId);

    Optional<RemoteCommand> findByIdempotencyKey(String idempotencyKey);

    List<RemoteCommand> findAllByVehicleIdAndStatusIn(UUID vehicleId, List<CommandStatus> statuses);

    @Query("SELECT c FROM RemoteCommand c WHERE c.status IN ('PENDING', 'SENT') AND c.requestedAt < :cutoff")
    List<RemoteCommand> findStaleCommands(@Param("cutoff") Instant cutoff);
}
