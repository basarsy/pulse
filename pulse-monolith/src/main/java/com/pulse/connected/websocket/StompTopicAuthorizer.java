package com.pulse.connected.websocket;

import com.pulse.connected.auth.Role;
import com.pulse.connected.auth.UserPrincipal;
import com.pulse.connected.vehicle.VehicleSecurityService;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides which STOMP topics a user may subscribe to. Topics carry live vehicle and rollout
 * data, so the rules mirror the REST API:
 * <ul>
 *   <li>{@code /topic/vehicles/{id}/commands|update-status}: users with access to that vehicle;</li>
 *   <li>everything under {@code /topic/} (rollouts, wildcard subscriptions): fleet and system admins;</li>
 *   <li>anything else: nobody.</li>
 * </ul>
 */
@Component
class StompTopicAuthorizer {

    private static final Pattern VEHICLE_TOPIC =
            Pattern.compile("^/topic/vehicles/([0-9a-fA-F-]{36})/(commands|update-status)$");
    private static final Set<Role> ADMIN_ROLES = Set.of(Role.FLEET_ADMIN, Role.SYSTEM_ADMIN);

    private final VehicleSecurityService vehicleSecurityService;

    StompTopicAuthorizer(VehicleSecurityService vehicleSecurityService) {
        this.vehicleSecurityService = vehicleSecurityService;
    }

    boolean canSubscribe(UserPrincipal principal, String destination) {
        if (principal == null || destination == null || !destination.startsWith("/topic/")) {
            return false;
        }
        if (principal.getUser().getRoles().stream().anyMatch(ADMIN_ROLES::contains)) {
            return true;
        }
        Matcher vehicleTopic = VEHICLE_TOPIC.matcher(destination);
        if (!vehicleTopic.matches()) {
            return false;
        }
        UUID vehicleId;
        try {
            vehicleId = UUID.fromString(vehicleTopic.group(1));
        } catch (IllegalArgumentException e) {
            return false;
        }
        return vehicleSecurityService.hasAccess(vehicleId, principal);
    }
}
