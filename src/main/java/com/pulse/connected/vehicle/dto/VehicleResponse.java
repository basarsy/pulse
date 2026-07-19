package com.pulse.connected.vehicle.dto;

import com.pulse.connected.vehicle.ConnectivityState;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public class VehicleResponse {

    private UUID id;
    private String vin;
    private String model;
    private Integer modelYear;
    private UUID ownerId;
    private String ownerEmail;
    private Set<UUID> authorizedUserIds;
    private ConnectivityState connectivityState;
    private String currentSoftwareVersion;
    private Instant lastSeenAt;
    private Double batteryOrFuelLevel;
    private Double odometerKm;
    private Double lastLat;
    private Double lastLng;
    private Instant createdAt;

    public VehicleResponse() {}

    public VehicleResponse(UUID id, String vin, String model, Integer modelYear, UUID ownerId, String ownerEmail, Set<UUID> authorizedUserIds, ConnectivityState connectivityState, String currentSoftwareVersion, Instant lastSeenAt, Double batteryOrFuelLevel, Double odometerKm, Double lastLat, Double lastLng, Instant createdAt) {
        this.id = id;
        this.vin = vin;
        this.model = model;
        this.modelYear = modelYear;
        this.ownerId = ownerId;
        this.ownerEmail = ownerEmail;
        this.authorizedUserIds = authorizedUserIds;
        this.connectivityState = connectivityState;
        this.currentSoftwareVersion = currentSoftwareVersion;
        this.lastSeenAt = lastSeenAt;
        this.batteryOrFuelLevel = batteryOrFuelLevel;
        this.odometerKm = odometerKm;
        this.lastLat = lastLat;
        this.lastLng = lastLng;
        this.createdAt = createdAt;
    }

    public static VehicleResponseBuilder builder() {
        return new VehicleResponseBuilder();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getVin() { return vin; }
    public void setVin(String vin) { this.vin = vin; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Integer getModelYear() { return modelYear; }
    public void setModelYear(Integer modelYear) { this.modelYear = modelYear; }

    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }

    public String getOwnerEmail() { return ownerEmail; }
    public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }

    public Set<UUID> getAuthorizedUserIds() { return authorizedUserIds; }
    public void setAuthorizedUserIds(Set<UUID> authorizedUserIds) { this.authorizedUserIds = authorizedUserIds; }

    public ConnectivityState getConnectivityState() { return connectivityState; }
    public void setConnectivityState(ConnectivityState connectivityState) { this.connectivityState = connectivityState; }

    public String getCurrentSoftwareVersion() { return currentSoftwareVersion; }
    public void setCurrentSoftwareVersion(String currentSoftwareVersion) { this.currentSoftwareVersion = currentSoftwareVersion; }

    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    public Double getBatteryOrFuelLevel() { return batteryOrFuelLevel; }
    public void setBatteryOrFuelLevel(Double batteryOrFuelLevel) { this.batteryOrFuelLevel = batteryOrFuelLevel; }

    public Double getOdometerKm() { return odometerKm; }
    public void setOdometerKm(Double odometerKm) { this.odometerKm = odometerKm; }

    public Double getLastLat() { return lastLat; }
    public void setLastLat(Double lastLat) { this.lastLat = lastLat; }

    public Double getLastLng() { return lastLng; }
    public void setLastLng(Double lastLng) { this.lastLng = lastLng; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static class VehicleResponseBuilder {
        private UUID id;
        private String vin;
        private String model;
        private Integer modelYear;
        private UUID ownerId;
        private String ownerEmail;
        private Set<UUID> authorizedUserIds;
        private ConnectivityState connectivityState;
        private String currentSoftwareVersion;
        private Instant lastSeenAt;
        private Double batteryOrFuelLevel;
        private Double odometerKm;
        private Double lastLat;
        private Double lastLng;
        private Instant createdAt;

        VehicleResponseBuilder() {}

        public VehicleResponseBuilder id(UUID id) { this.id = id; return this; }
        public VehicleResponseBuilder vin(String vin) { this.vin = vin; return this; }
        public VehicleResponseBuilder model(String model) { this.model = model; return this; }
        public VehicleResponseBuilder modelYear(Integer modelYear) { this.modelYear = modelYear; return this; }
        public VehicleResponseBuilder ownerId(UUID ownerId) { this.ownerId = ownerId; return this; }
        public VehicleResponseBuilder ownerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; return this; }
        public VehicleResponseBuilder authorizedUserIds(Set<UUID> authorizedUserIds) { this.authorizedUserIds = authorizedUserIds; return this; }
        public VehicleResponseBuilder connectivityState(ConnectivityState connectivityState) { this.connectivityState = connectivityState; return this; }
        public VehicleResponseBuilder currentSoftwareVersion(String currentSoftwareVersion) { this.currentSoftwareVersion = currentSoftwareVersion; return this; }
        public VehicleResponseBuilder lastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; return this; }
        public VehicleResponseBuilder batteryOrFuelLevel(Double batteryOrFuelLevel) { this.batteryOrFuelLevel = batteryOrFuelLevel; return this; }
        public VehicleResponseBuilder odometerKm(Double odometerKm) { this.odometerKm = odometerKm; return this; }
        public VehicleResponseBuilder lastLat(Double lastLat) { this.lastLat = lastLat; return this; }
        public VehicleResponseBuilder lastLng(Double lastLng) { this.lastLng = lastLng; return this; }
        public VehicleResponseBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public VehicleResponse build() {
            return new VehicleResponse(id, vin, model, modelYear, ownerId, ownerEmail, authorizedUserIds, connectivityState, currentSoftwareVersion, lastSeenAt, batteryOrFuelLevel, odometerKm, lastLat, lastLng, createdAt);
        }
    }
}
