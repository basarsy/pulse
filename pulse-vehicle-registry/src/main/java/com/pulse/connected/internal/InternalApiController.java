package com.pulse.connected.internal;

import com.pulse.connected.auth.User;
import com.pulse.connected.auth.UserRepository;
import com.pulse.connected.vehicle.Vehicle;
import com.pulse.connected.vehicle.VehicleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/internal")
public class InternalApiController {

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;

    public InternalApiController(VehicleRepository vehicleRepository, UserRepository userRepository) {
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
    }

    public record VehicleInfoResponse(
            UUID id,
            String vin,
            String model,
            int modelYear,
            String connectivityState,
            String currentSoftwareVersion,
            UUID ownerId
    ) {
        public static VehicleInfoResponse from(Vehicle v) {
            return new VehicleInfoResponse(
                    v.getId(),
                    v.getVin(),
                    v.getModel(),
                    v.getModelYear(),
                    v.getConnectivityState() != null ? v.getConnectivityState().name() : "OFFLINE",
                    v.getCurrentSoftwareVersion(),
                    v.getOwner() != null ? v.getOwner().getId() : null
            );
        }
    }

    public record UserInfoResponse(
            UUID id,
            String email,
            String fullName
    ) {
        public static UserInfoResponse from(User u) {
            return new UserInfoResponse(u.getId(), u.getEmail(), u.getFullName());
        }
    }

    @GetMapping("/vehicles/{id}")
    public ResponseEntity<VehicleInfoResponse> getVehicle(@PathVariable UUID id) {
        return vehicleRepository.findById(id)
                .map(VehicleInfoResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/vehicles")
    public ResponseEntity<List<VehicleInfoResponse>> getVehiclesByModel(@RequestParam(required = false) String model) {
        List<VehicleInfoResponse> vehicles = vehicleRepository.findAll().stream()
                .filter(v -> model == null || model.equalsIgnoreCase(v.getModel()))
                .map(VehicleInfoResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(vehicles);
    }

    @GetMapping("/vehicles/{id}/authorized-users")
    public ResponseEntity<List<UUID>> getAuthorizedUsers(@PathVariable UUID id) {
        return vehicleRepository.findById(id)
                .map(vehicle -> ResponseEntity.ok(
                        vehicle.getAuthorizedUsers().stream().map(User::getId).collect(Collectors.toList())
                ))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserInfoResponse> getUser(@PathVariable UUID id) {
        return userRepository.findById(id)
                .map(UserInfoResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/vehicles/{id}/access-check")
    public ResponseEntity<Boolean> checkAccess(@PathVariable UUID id, @RequestParam UUID userId) {
        return vehicleRepository.findById(id)
                .map(vehicle -> {
                    boolean isOwner = vehicle.getOwner() != null && vehicle.getOwner().getId().equals(userId);
                    boolean isAuthorized = vehicle.getAuthorizedUsers().stream().anyMatch(u -> u.getId().equals(userId));
                    return ResponseEntity.ok(isOwner || isAuthorized);
                })
                .orElse(ResponseEntity.ok(false));
    }

    @PatchMapping("/vehicles/{id}/software-version")
    @Transactional
    public ResponseEntity<Void> updateSoftwareVersion(@PathVariable UUID id, @RequestParam String versionLabel) {
        return vehicleRepository.findById(id)
                .map(vehicle -> {
                    vehicle.setCurrentSoftwareVersion(versionLabel);
                    vehicleRepository.save(vehicle);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
