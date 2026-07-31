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
        if (vehicleRepository.existsByVin(request.getVin().toUpperCase())) {
            throw new DuplicateResourceException("Vehicle with VIN '" + request.getVin() + "' already exists");
        }

        Vehicle vehicle = Vehicle.builder()
                .vin(request.getVin().toUpperCase())
                .model(request.getModel())
                .modelYear(request.getModelYear())
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

        User familyUser = userRepository.findByEmail(request.getUserEmail().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + request.getUserEmail()));

        if (vehicle.getOwner() != null && vehicle.getOwner().getId().equals(familyUser.getId())) {
            throw new IllegalArgumentException("User is already the owner of this vehicle");
        }

        vehicle.getAuthorizedUsers().add(familyUser);
        Vehicle updated = vehicleRepository.save(vehicle);
        
        UUID actorId = vehicle.getOwner() != null ? vehicle.getOwner().getId() : null;
        auditLogService.log(actorId, "VEHICLE_AUTHORIZED", "Vehicle", vehicleId.toString(), Map.of("authorizedUserEmail", request.getUserEmail()));
        
        return mapToResponse(updated);
    }

    public VehicleResponse mapToResponse(Vehicle vehicle) {
        return VehicleResponse.builder()
                .id(vehicle.getId())
                .vin(vehicle.getVin())
                .model(vehicle.getModel())
                .modelYear(vehicle.getModelYear())
                .ownerId(vehicle.getOwner() != null ? vehicle.getOwner().getId() : null)
                .ownerEmail(vehicle.getOwner() != null ? vehicle.getOwner().getEmail() : null)
                .authorizedUserIds(vehicle.getAuthorizedUsers().stream().map(User::getId).collect(Collectors.toSet()))
                .connectivityState(vehicle.getConnectivityState())
                .currentSoftwareVersion(vehicle.getCurrentSoftwareVersion())
                .lastSeenAt(vehicle.getLastSeenAt())
                .batteryOrFuelLevel(vehicle.getBatteryOrFuelLevel())
                .odometerKm(vehicle.getOdometerKm())
                .lastLat(vehicle.getLastLat())
                .lastLng(vehicle.getLastLng())
                .createdAt(vehicle.getCreatedAt())
                .build();
    }
}
