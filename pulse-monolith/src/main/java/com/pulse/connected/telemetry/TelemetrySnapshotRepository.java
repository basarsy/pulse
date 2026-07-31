package com.pulse.connected.telemetry;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TelemetrySnapshotRepository extends JpaRepository<TelemetrySnapshot, UUID> {
    Optional<TelemetrySnapshot> findTopByVehicleIdOrderByRecordedAtDesc(UUID vehicleId);
    List<TelemetrySnapshot> findAllByVehicleIdOrderByRecordedAtDesc(UUID vehicleId, Pageable pageable);
}
