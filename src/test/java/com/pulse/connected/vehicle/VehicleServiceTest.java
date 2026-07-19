package com.pulse.connected.vehicle;

import com.pulse.connected.auth.Role;
import com.pulse.connected.auth.User;
import com.pulse.connected.auth.UserRepository;
import com.pulse.connected.common.exception.DuplicateResourceException;
import com.pulse.connected.vehicle.dto.AuthorizeUserRequest;
import com.pulse.connected.vehicle.dto.CreateVehicleRequest;
import com.pulse.connected.vehicle.dto.VehicleResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private VehicleService vehicleService;

    private User owner;
    private User familyUser;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        owner = User.builder()
                .id(UUID.randomUUID())
                .email("owner@example.com")
                .fullName("Owner Name")
                .roles(Set.of(Role.OWNER))
                .build();

        familyUser = User.builder()
                .id(UUID.randomUUID())
                .email("family@example.com")
                .fullName("Family Member")
                .roles(Set.of(Role.FAMILY_MEMBER))
                .build();

        vehicle = Vehicle.builder()
                .id(UUID.randomUUID())
                .vin("WBA12345678901234")
                .model("BMW iX xDrive50")
                .modelYear(2026)
                .owner(owner)
                .authorizedUsers(new HashSet<>())
                .connectivityState(ConnectivityState.OFFLINE)
                .currentSoftwareVersion("1.0.0")
                .build();
    }

    @Test
    void registerVehicle_Success() {
        CreateVehicleRequest request = CreateVehicleRequest.builder()
                .vin("WBA12345678901234")
                .model("BMW iX xDrive50")
                .modelYear(2026)
                .build();

        when(vehicleRepository.existsByVin("WBA12345678901234")).thenReturn(false);
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(vehicle);

        VehicleResponse response = vehicleService.registerVehicle(request, owner);

        assertThat(response).isNotNull();
        assertThat(response.getVin()).isEqualTo("WBA12345678901234");
        assertThat(response.getOwnerEmail()).isEqualTo("owner@example.com");
        verify(vehicleRepository).save(any(Vehicle.class));
    }

    @Test
    void registerVehicle_DuplicateVin_ThrowsException() {
        CreateVehicleRequest request = CreateVehicleRequest.builder()
                .vin("WBA12345678901234")
                .model("BMW iX xDrive50")
                .modelYear(2026)
                .build();

        when(vehicleRepository.existsByVin("WBA12345678901234")).thenReturn(true);

        assertThatThrownBy(() -> vehicleService.registerVehicle(request, owner))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Vehicle with VIN 'WBA12345678901234' already exists");
    }

    @Test
    void authorizeFamilyMember_Success() {
        AuthorizeUserRequest request = AuthorizeUserRequest.builder()
                .userEmail("family@example.com")
                .build();

        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail("family@example.com")).thenReturn(Optional.of(familyUser));
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        VehicleResponse response = vehicleService.authorizeFamilyMember(vehicle.getId(), request);

        assertThat(response).isNotNull();
        assertThat(response.getAuthorizedUserIds()).contains(familyUser.getId());
        verify(vehicleRepository).save(vehicle);
    }
}
