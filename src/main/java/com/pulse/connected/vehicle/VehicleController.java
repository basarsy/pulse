package com.pulse.connected.vehicle;

import com.pulse.connected.security.UserPrincipal;
import com.pulse.connected.vehicle.dto.AuthorizeUserRequest;
import com.pulse.connected.vehicle.dto.CreateVehicleRequest;
import com.pulse.connected.vehicle.dto.VehicleResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'DEALER_STAFF', 'SYSTEM_ADMIN')")
    public ResponseEntity<VehicleResponse> registerVehicle(
            @Valid @RequestBody CreateVehicleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        VehicleResponse response = vehicleService.registerVehicle(request, principal.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@vehicleSecurityService.hasAccess(#id, principal)")
    public ResponseEntity<VehicleResponse> getVehicleById(@PathVariable UUID id) {
        VehicleResponse response = vehicleService.getVehicleById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<VehicleResponse>> getMyVehicles(@AuthenticationPrincipal UserPrincipal principal) {
        List<VehicleResponse> response = vehicleService.getMyVehicles(principal.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/authorize")
    @PreAuthorize("@vehicleSecurityService.isOwner(#id, principal)")
    public ResponseEntity<VehicleResponse> authorizeFamilyMember(
            @PathVariable UUID id,
            @Valid @RequestBody AuthorizeUserRequest request) {
        VehicleResponse response = vehicleService.authorizeFamilyMember(id, request);
        return ResponseEntity.ok(response);
    }
}
