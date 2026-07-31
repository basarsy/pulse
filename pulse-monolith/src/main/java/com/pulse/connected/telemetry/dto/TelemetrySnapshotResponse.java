package com.pulse.connected.telemetry.dto;

import java.time.Instant;
import java.util.UUID;

public class TelemetrySnapshotResponse {

    private UUID id;
    private UUID vehicleId;
    private Instant recordedAt;
    private Double batteryOrFuelLevel;
    private Double odometerKm;
    private Double engineTempC;
    private Double lat;
    private Double lng;

    public TelemetrySnapshotResponse() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(UUID vehicleId) {
        this.vehicleId = vehicleId;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }

    public Double getBatteryOrFuelLevel() {
        return batteryOrFuelLevel;
    }

    public void setBatteryOrFuelLevel(Double batteryOrFuelLevel) {
        this.batteryOrFuelLevel = batteryOrFuelLevel;
    }

    public Double getOdometerKm() {
        return odometerKm;
    }

    public void setOdometerKm(Double odometerKm) {
        this.odometerKm = odometerKm;
    }

    public Double getEngineTempC() {
        return engineTempC;
    }

    public void setEngineTempC(Double engineTempC) {
        this.engineTempC = engineTempC;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLng() {
        return lng;
    }

    public void setLng(Double lng) {
        this.lng = lng;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final TelemetrySnapshotResponse response = new TelemetrySnapshotResponse();

        public Builder id(UUID id) {
            response.setId(id);
            return this;
        }

        public Builder vehicleId(UUID vehicleId) {
            response.setVehicleId(vehicleId);
            return this;
        }

        public Builder recordedAt(Instant recordedAt) {
            response.setRecordedAt(recordedAt);
            return this;
        }

        public Builder batteryOrFuelLevel(Double batteryOrFuelLevel) {
            response.setBatteryOrFuelLevel(batteryOrFuelLevel);
            return this;
        }

        public Builder odometerKm(Double odometerKm) {
            response.setOdometerKm(odometerKm);
            return this;
        }

        public Builder engineTempC(Double engineTempC) {
            response.setEngineTempC(engineTempC);
            return this;
        }

        public Builder lat(Double lat) {
            response.setLat(lat);
            return this;
        }

        public Builder lng(Double lng) {
            response.setLng(lng);
            return this;
        }

        public TelemetrySnapshotResponse build() {
            return response;
        }
    }
}
