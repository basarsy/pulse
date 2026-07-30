package com.pulse.connected.telemetry;

import com.pulse.connected.messaging.dto.VehicleTelemetryEvent;
import com.pulse.connected.telemetry.dto.IngestTelemetryRequest;
import com.pulse.connected.telemetry.dto.TelemetrySnapshotResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vehicles/{id}/telemetry")
public class TelemetryController {

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @PostMapping
    public ResponseEntity<Void> ingestTelemetry(@PathVariable("id") UUID id, @RequestBody IngestTelemetryRequest request) {
        VehicleTelemetryEvent event = VehicleTelemetryEvent.builder()
                .vehicleId(id)
                .batteryOrFuelLevel(request.getBatteryOrFuelLevel())
                .odometerKm(request.getOdometerKm())
                .engineTempC(request.getEngineTempC())
                .lat(request.getLat())
                .lng(request.getLng())
                .timestamp(Instant.now())
                .build();

        telemetryService.ingestTelemetry(event);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/latest")
    @PreAuthorize("@vehicleSecurityService.hasAccess(#id, principal)")
    public ResponseEntity<TelemetrySnapshotResponse> getLatestSnapshot(@PathVariable("id") UUID id) {
        TelemetrySnapshotResponse response = telemetryService.getLatestSnapshot(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    @PreAuthorize("@vehicleSecurityService.hasAccess(#id, principal)")
    public ResponseEntity<List<TelemetrySnapshotResponse>> getSnapshotHistory(
            @PathVariable("id") UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        List<TelemetrySnapshotResponse> history = telemetryService.getSnapshotHistory(id, page, size);
        return ResponseEntity.ok(history);
    }
}
