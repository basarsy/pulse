package com.pulse.connected.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class VehicleRegistryClient {
    private final RestClient restClient;

    public VehicleRegistryClient(@Value("${pulse.services.vehicle-registry.url:http://localhost:8081}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public VehicleInfo getVehicle(UUID vehicleId) {
        try {
            return restClient.get()
                    .uri("/internal/vehicles/{id}", vehicleId)
                    .retrieve()
                    .body(VehicleInfo.class);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean hasAccess(UUID vehicleId, UUID userId) {
        try {
            Boolean result = restClient.get()
                    .uri("/internal/vehicles/{id}/access-check?userId={userId}", vehicleId, userId)
                    .retrieve()
                    .body(Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    public UserInfo getUser(UUID userId) {
        try {
            return restClient.get()
                    .uri("/internal/users/{id}", userId)
                    .retrieve()
                    .body(UserInfo.class);
        } catch (Exception e) {
            return null;
        }
    }

    public record VehicleInfo(UUID id, String vin, String model, int modelYear, String connectivityState, String currentSoftwareVersion, UUID ownerId) {}
    public record UserInfo(UUID id, String email, String fullName) {}
}
