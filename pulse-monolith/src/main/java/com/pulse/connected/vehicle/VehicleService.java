package com.pulse.connected.vehicle;

import com.pulse.connected.domain.ConnectivityState;

import com.pulse.connected.auth.User;
import com.pulse.connected.auth.UserRepository;
import com.pulse.connected.common.exception.DuplicateResourceException;
import com.pulse.connected.common.exception.ResourceNotFoundException;
import com.pulse.connected.vehicle.dto.AuthorizeUserRequest;
import com.pulse.connected.vehicle.dto.CreateVehicleRequest;
import com.pulse.connected.vehicle.dto.VehicleResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.pulse.connected.common.AuditLogService;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public VehicleService(VehicleRepository vehicleRepository, UserRepository userRepository, AuditLogService auditLogService) {
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public VehicleResponse registerVehicle(CreateVehicleRequest request, User currentUser) {
        if (vehicleRepository.existsByVin(request.vin().toUpperCase())) {
            throw new DuplicateResourceException("Vehicle with VIN '" + request.vin() + "' already exists");
        }

        Vehicle vehicle = Vehicle.builder()
                .vin(request.vin().toUpperCase())
                .model(request.model())
                .modelYear(request.modelYear())
                .owner(currentUser)
                .connectivityState(ConnectivityState.OFFLINE)
                .currentSoftwareVersion("1.0.0")
                .batteryOrFuelLevel(100.0)
                .odometerKm(0.0)
                .build();

        Vehicle saved = vehicleRepository.save(vehicle);
        auditLogService.log(currentUser.getId(), "VEHICLE_REGISTERED", "Vehicle", saved.getId().toString(), null);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public VehicleResponse getVehicleById(UUID vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with ID: " + vehicleId));
        return mapToResponse(vehicle);
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> getMyVehicles(UUID userId) {
        List<Vehicle> vehicles = vehicleRepository.findAllAccessibleByUserId(userId);
        return vehicles.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> getAllFleetVehicles() {
        return vehicleRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public VehicleResponse authorizeFamilyMember(UUID vehicleId, AuthorizeUserRequest request) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with ID: " + vehicleId));

        User familyUser = userRepository.findByEmail(request.userEmail().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + request.userEmail()));

        if (vehicle.getOwner() != null && vehicle.getOwner().getId().equals(familyUser.getId())) {
            throw new IllegalArgumentException("User is already the owner of this vehicle");
        }

        vehicle.getAuthorizedUsers().add(familyUser);
        Vehicle updated = vehicleRepository.save(vehicle);
        
        UUID actorId = vehicle.getOwner() != null ? vehicle.getOwner().getId() : null;
        auditLogService.log(actorId, "VEHICLE_AUTHORIZED", "Vehicle", vehicleId.toString(), Map.of("authorizedUserEmail", request.userEmail()));
        
        return mapToResponse(updated);
    }

    public VehicleResponse mapToResponse(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getVin(),
                vehicle.getModel(),
                vehicle.getModelYear(),
                vehicle.getOwner() != null ? vehicle.getOwner().getId() : null,
                vehicle.getOwner() != null ? vehicle.getOwner().getEmail() : null,
                vehicle.getAuthorizedUsers().stream().map(User::getId).collect(Collectors.toSet()),
                vehicle.getConnectivityState(),
                vehicle.getCurrentSoftwareVersion(),
                vehicle.getLastSeenAt(),
                vehicle.getBatteryOrFuelLevel(),
                vehicle.getOdometerKm(),
                vehicle.getLastLat(),
                vehicle.getLastLng(),
                vehicle.getCreatedAt());
    }
}
