package com.pulse.connected.vehicle;

import com.pulse.connected.vehicle.dto.VehicleResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fleet/vehicles")
public class FleetVehicleController {

    private final VehicleService vehicleService;

    public FleetVehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FLEET_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<List<VehicleResponse>> getAllFleetVehicles() {
        List<VehicleResponse> vehicles = vehicleService.getAllFleetVehicles();
        return ResponseEntity.ok(vehicles);
    }
}
