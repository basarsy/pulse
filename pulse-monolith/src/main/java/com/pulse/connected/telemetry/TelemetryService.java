package com.pulse.connected.telemetry;

import com.pulse.connected.common.exception.ResourceNotFoundException;
import com.pulse.connected.messaging.dto.VehicleTelemetryEvent;
import com.pulse.connected.telemetry.dto.TelemetrySnapshotResponse;
import com.pulse.connected.vehicle.Vehicle;
import com.pulse.connected.vehicle.VehicleRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        Vehicle vehicle = vehicleRepository.findById(event.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with ID: " + event.getVehicleId()));

        TelemetrySnapshot snapshot = TelemetrySnapshot.builder()
                .vehicle(vehicle)
                .batteryOrFuelLevel(event.getBatteryOrFuelLevel())
                .odometerKm(event.getOdometerKm())
                .engineTempC(event.getEngineTempC())
                .lat(event.getLat())
                .lng(event.getLng())
                .build();
                
        if (event.getTimestamp() != null) {
            snapshot.setRecordedAt(event.getTimestamp());
        }

        telemetrySnapshotRepository.save(snapshot);

        vehicle.setBatteryOrFuelLevel(event.getBatteryOrFuelLevel());
        vehicle.setOdometerKm(event.getOdometerKm());
        vehicle.setLastLat(event.getLat());
        vehicle.setLastLng(event.getLng());
        vehicle.setLastSeenAt(event.getTimestamp());
        vehicleRepository.save(vehicle);

        meterRegistry.counter("pulse.telemetry.ingested").increment();
        log.debug("Ingested telemetry for vehicle: {}", vehicle.getId());
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
        return TelemetrySnapshotResponse.builder()
                .id(snapshot.getId())
                .vehicleId(snapshot.getVehicle().getId())
                .recordedAt(snapshot.getRecordedAt())
                .batteryOrFuelLevel(snapshot.getBatteryOrFuelLevel())
                .odometerKm(snapshot.getOdometerKm())
                .engineTempC(snapshot.getEngineTempC())
                .lat(snapshot.getLat())
                .lng(snapshot.getLng())
                .build();
    }
}
