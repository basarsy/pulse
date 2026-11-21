package com.pulse.connected.telemetry;

import com.pulse.connected.common.exception.ResourceNotFoundException;
import com.pulse.connected.messaging.dto.VehicleTelemetryEvent;
import com.pulse.connected.telemetry.dto.TelemetrySnapshotResponse;
import com.pulse.connected.vehicle.VehicleRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private final TelemetrySnapshotRepository telemetrySnapshotRepository;
    private final VehicleRepository vehicleRepository;
    private final MeterRegistry meterRegistry;

    public TelemetryService(
            TelemetrySnapshotRepository telemetrySnapshotRepository,
            VehicleRepository vehicleRepository,
            MeterRegistry meterRegistry) {
        this.telemetrySnapshotRepository = telemetrySnapshotRepository;
        this.vehicleRepository = vehicleRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public void ingestTelemetry(VehicleTelemetryEvent event) {
        // Only the telemetry columns are written, not the whole row: heartbeats and OTA installs
        // change the same vehicle concurrently, and saving a loaded copy could revert their changes.
        // Server time, not the vehicle's clock: offline detection relies on lastSeenAt.
        int updated = vehicleRepository.updateTelemetry(event.vehicleId(), event.batteryOrFuelLevel(),
                event.odometerKm(), event.lat(), event.lng(), Instant.now());
        if (updated == 0) {
            throw new ResourceNotFoundException("Vehicle not found with ID: " + event.vehicleId());
        }

        TelemetrySnapshot snapshot = TelemetrySnapshot.builder()
                .vehicle(vehicleRepository.getReferenceById(event.vehicleId()))
                .batteryOrFuelLevel(event.batteryOrFuelLevel())
                .odometerKm(event.odometerKm())
                .engineTempC(event.engineTempC())
                .lat(event.lat())
                .lng(event.lng())
                .build();
                
        if (event.timestamp() != null) {
            snapshot.setRecordedAt(event.timestamp());
        }

        telemetrySnapshotRepository.save(snapshot);

        meterRegistry.counter("pulse.telemetry.ingested").increment();
        log.debug("Ingested telemetry for vehicle: {}", event.vehicleId());
    }

    @Transactional(readOnly = true)
    public TelemetrySnapshotResponse getLatestSnapshot(UUID vehicleId) {
        if (!vehicleRepository.existsById(vehicleId)) {
            throw new ResourceNotFoundException("Vehicle not found with ID: " + vehicleId);
        }

        return telemetrySnapshotRepository.findTopByVehicleIdOrderByRecordedAtDesc(vehicleId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No telemetry data found for vehicle ID: " + vehicleId));
    }

    @Transactional(readOnly = true)
    public List<TelemetrySnapshotResponse> getSnapshotHistory(UUID vehicleId, int page, int size) {
        if (!vehicleRepository.existsById(vehicleId)) {
            throw new ResourceNotFoundException("Vehicle not found with ID: " + vehicleId);
        }

        return telemetrySnapshotRepository.findAllByVehicleIdOrderByRecordedAtDesc(vehicleId, PageRequest.of(page, size))
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private TelemetrySnapshotResponse mapToResponse(TelemetrySnapshot snapshot) {
        return new TelemetrySnapshotResponse(
                snapshot.getId(),
                snapshot.getVehicle().getId(),
                snapshot.getRecordedAt(),
                snapshot.getBatteryOrFuelLevel(),
                snapshot.getOdometerKm(),
                snapshot.getEngineTempC(),
                snapshot.getLat(),
                snapshot.getLng());
    }
}
