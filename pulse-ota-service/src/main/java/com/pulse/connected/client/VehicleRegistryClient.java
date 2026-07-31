package com.pulse.connected.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
public class VehicleRegistryClient {

    private final RestClient restClient;

    public VehicleRegistryClient(@Value("${pulse.services.vehicle-registry.url:http://localhost:8081}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public List<VehicleInfo> getVehiclesByModel(String model) {
        try {
            VehicleInfo[] response = restClient.get()
                    .uri("/internal/vehicles?model={model}", model)
                    .retrieve()
                    .body(VehicleInfo[].class);
            return response != null ? List.of(response) : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public VehicleInfo getVehicle(UUID id) {
        try {
            return restClient.get()
                    .uri("/internal/vehicles/{id}", id)
                    .retrieve()
                    .body(VehicleInfo.class);
        } catch (Exception e) {
            return null;
        }
    }

    public UserInfo getUser(UUID id) {
        try {
            return restClient.get()
                    .uri("/internal/users/{id}", id)
                    .retrieve()
                    .body(UserInfo.class);
        } catch (Exception e) {
            return null;
        }
    }

    public void updateVehicleSoftwareVersion(UUID vehicleId, String versionLabel) {
        try {
            restClient.patch()
                    .uri("/internal/vehicles/{id}/software-version?versionLabel={versionLabel}", vehicleId, versionLabel)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // Log or ignore internal update error
        }
    }

    public record VehicleInfo(UUID id, String vin, String model, int modelYear, String connectivityState, String currentSoftwareVersion, UUID ownerId) {}
    public record UserInfo(UUID id, String email, String fullName) {}
}
