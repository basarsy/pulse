package com.pulse.connected.telemetry;

import com.pulse.connected.common.exception.ResourceNotFoundException;
import com.pulse.connected.messaging.dto.VehicleTelemetryEvent;
import com.pulse.connected.telemetry.dto.TelemetrySnapshotResponse;
import com.pulse.connected.vehicle.Vehicle;
import com.pulse.connected.vehicle.VehicleRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryServiceTest {

    @Mock
    private TelemetrySnapshotRepository telemetrySnapshotRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private TelemetryService telemetryService;

    private Vehicle testVehicle;
    private UUID vehicleId;

    @BeforeEach
    void setUp() {
        vehicleId = UUID.randomUUID();
        testVehicle = new Vehicle();
        testVehicle.setId(vehicleId);
        testVehicle.setVin("VIN12345");
    }

    @Test
    void ingestTelemetry_savesSnapshotAndUpdatesVehicle() {
        VehicleTelemetryEvent event = VehicleTelemetryEvent.builder()
                .vehicleId(vehicleId)
                .batteryOrFuelLevel(85.5)
                .odometerKm(12000.0)
                .engineTempC(90.0)
                .lat(48.1351)
                .lng(11.5820)
                .timestamp(Instant.now())
                .build();

        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(testVehicle));

        telemetryService.ingestTelemetry(event);

        ArgumentCaptor<TelemetrySnapshot> snapshotCaptor = ArgumentCaptor.forClass(TelemetrySnapshot.class);
        verify(telemetrySnapshotRepository).save(snapshotCaptor.capture());
        TelemetrySnapshot savedSnapshot = snapshotCaptor.getValue();

        assertEquals(vehicleId, savedSnapshot.getVehicle().getId());
        assertEquals(85.5, savedSnapshot.getBatteryOrFuelLevel());
        assertEquals(12000.0, savedSnapshot.getOdometerKm());
        assertEquals(90.0, savedSnapshot.getEngineTempC());
        assertEquals(48.1351, savedSnapshot.getLat());
        assertEquals(11.5820, savedSnapshot.getLng());
        assertEquals(event.getTimestamp(), savedSnapshot.getRecordedAt());

        ArgumentCaptor<Vehicle> vehicleCaptor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(vehicleCaptor.capture());
        Vehicle updatedVehicle = vehicleCaptor.getValue();

        assertEquals(85.5, updatedVehicle.getBatteryOrFuelLevel());
        assertEquals(12000.0, updatedVehicle.getOdometerKm());
        assertEquals(48.1351, updatedVehicle.getLastLat());
        assertEquals(11.5820, updatedVehicle.getLastLng());
        assertEquals(event.getTimestamp(), updatedVehicle.getLastSeenAt());

        assertEquals(1.0, meterRegistry.counter("pulse.telemetry.ingested").count());
    }

    @Test
    void ingestTelemetry_vehicleNotFound_throwsException() {
        VehicleTelemetryEvent event = VehicleTelemetryEvent.builder()
                .vehicleId(vehicleId)
                .build();

        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> telemetryService.ingestTelemetry(event));
        verify(telemetrySnapshotRepository, never()).save(any());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void getLatestSnapshot_returnsCorrectData() {
        when(vehicleRepository.existsById(vehicleId)).thenReturn(true);

        TelemetrySnapshot snapshot = new TelemetrySnapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setVehicle(testVehicle);
        snapshot.setBatteryOrFuelLevel(50.0);
        snapshot.setOdometerKm(100.0);
        snapshot.setEngineTempC(85.0);
        snapshot.setLat(1.0);
        snapshot.setLng(2.0);
        Instant now = Instant.now();
        snapshot.setRecordedAt(now);

        when(telemetrySnapshotRepository.findTopByVehicleIdOrderByRecordedAtDesc(vehicleId))
                .thenReturn(Optional.of(snapshot));

        TelemetrySnapshotResponse response = telemetryService.getLatestSnapshot(vehicleId);

        assertNotNull(response);
        assertEquals(snapshot.getId(), response.getId());
        assertEquals(vehicleId, response.getVehicleId());
        assertEquals(50.0, response.getBatteryOrFuelLevel());
        assertEquals(100.0, response.getOdometerKm());
        assertEquals(85.0, response.getEngineTempC());
        assertEquals(1.0, response.getLat());
        assertEquals(2.0, response.getLng());
        assertEquals(now, response.getRecordedAt());
    }

    @Test
    void getLatestSnapshot_vehicleNotFound_throwsException() {
        when(vehicleRepository.existsById(vehicleId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> telemetryService.getLatestSnapshot(vehicleId));
        verify(telemetrySnapshotRepository, never()).findTopByVehicleIdOrderByRecordedAtDesc(any());
    }
}
