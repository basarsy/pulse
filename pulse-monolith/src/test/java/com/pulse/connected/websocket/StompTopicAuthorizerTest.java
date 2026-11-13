package com.pulse.connected.websocket;

import com.pulse.connected.auth.Role;
import com.pulse.connected.auth.User;
import com.pulse.connected.auth.UserPrincipal;
import com.pulse.connected.vehicle.VehicleSecurityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StompTopicAuthorizerTest {

    @Mock
    private VehicleSecurityService vehicleSecurityService;

    @InjectMocks
    private StompTopicAuthorizer authorizer;

    private final UUID vehicleId = UUID.randomUUID();

    @Test
    void ownerMaySubscribeToTheirVehicleTopics() {
        UserPrincipal owner = principal(Role.OWNER);
        when(vehicleSecurityService.hasAccess(vehicleId, owner)).thenReturn(true);

        assertThat(authorizer.canSubscribe(owner, "/topic/vehicles/" + vehicleId + "/commands")).isTrue();
        assertThat(authorizer.canSubscribe(owner, "/topic/vehicles/" + vehicleId + "/update-status")).isTrue();
    }

    @Test
    void userWithoutVehicleAccessIsDenied() {
        UserPrincipal stranger = principal(Role.OWNER);
        when(vehicleSecurityService.hasAccess(vehicleId, stranger)).thenReturn(false);

        assertThat(authorizer.canSubscribe(stranger, "/topic/vehicles/" + vehicleId + "/commands")).isFalse();
    }

    @Test
    void nonAdminsCannotUseWildcardsOrRolloutTopics() {
        UserPrincipal owner = principal(Role.OWNER);

        assertThat(authorizer.canSubscribe(owner, "/topic/vehicles/*/commands")).isFalse();
        assertThat(authorizer.canSubscribe(owner, "/topic/vehicles/**")).isFalse();
        assertThat(authorizer.canSubscribe(owner, "/topic/rollouts/" + UUID.randomUUID())).isFalse();
        verifyNoInteractions(vehicleSecurityService);
    }

    @Test
    void fleetAdminsMaySubscribeToRolloutsAndFleetWideTopics() {
        UserPrincipal admin = principal(Role.FLEET_ADMIN);

        assertThat(authorizer.canSubscribe(admin, "/topic/rollouts/" + UUID.randomUUID())).isTrue();
        assertThat(authorizer.canSubscribe(admin, "/topic/rollouts/*")).isTrue();
        assertThat(authorizer.canSubscribe(admin, "/topic/vehicles/*/commands")).isTrue();
    }

    @Test
    void unknownDestinationsAndAnonymousUsersAreDenied() {
        assertThat(authorizer.canSubscribe(null, "/topic/vehicles/" + vehicleId + "/commands")).isFalse();
        assertThat(authorizer.canSubscribe(principal(Role.SYSTEM_ADMIN), "/queue/anything")).isFalse();
        assertThat(authorizer.canSubscribe(principal(Role.OWNER), "/topic/vehicles/not-a-uuid/commands")).isFalse();
        assertThat(authorizer.canSubscribe(principal(Role.OWNER), null)).isFalse();
        verify(vehicleSecurityService, never()).hasAccess(any(), any());
    }

    private static UserPrincipal principal(Role role) {
        return new UserPrincipal(User.builder()
                .id(UUID.randomUUID())
                .email(role.name().toLowerCase() + "@pulse.test")
                .fullName("Test")
                .roles(Set.of(role))
                .build());
    }
}
