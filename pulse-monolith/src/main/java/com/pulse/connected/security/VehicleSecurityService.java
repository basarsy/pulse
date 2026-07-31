package com.pulse.connected.security;

import com.pulse.connected.auth.Role;
import com.pulse.connected.auth.User;
import com.pulse.connected.vehicle.Vehicle;
import com.pulse.connected.vehicle.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service("vehicleSecurityService")
public class VehicleSecurityService {

    private final VehicleRepository vehicleRepository;

    public VehicleSecurityService(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    @Transactional(readOnly = true)
    public boolean hasAccess(UUID vehicleId, UserPrincipal principal) {
        if (principal == null || principal.getUser() == null) {
            return false;
        }

        User user = principal.getUser();

        // Admins have global access
        if (user.getRoles().contains(Role.FLEET_ADMIN) || user.getRoles().contains(Role.SYSTEM_ADMIN)) {
            return true;
        }

        return vehicleRepository.findById(vehicleId)
                .map(vehicle -> isOwnerOrAuthorized(vehicle, user.getId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isOwner(UUID vehicleId, UserPrincipal principal) {
        if (principal == null || principal.getUser() == null) {
            return false;
        }

        User user = principal.getUser();
        if (user.getRoles().contains(Role.SYSTEM_ADMIN)) {
            return true;
        }

        return vehicleRepository.findById(vehicleId)
                .map(vehicle -> vehicle.getOwner() != null && vehicle.getOwner().getId().equals(user.getId()))
                .orElse(false);
    }

    private boolean isOwnerOrAuthorized(Vehicle vehicle, UUID userId) {
        boolean isOwner = vehicle.getOwner() != null && vehicle.getOwner().getId().equals(userId);
        boolean isAuthorized = vehicle.getAuthorizedUsers().stream()
                .anyMatch(authUser -> authUser.getId().equals(userId));

        return isOwner || isAuthorized;
    }
}
