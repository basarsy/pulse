package com.pulse.connected.messaging.dto;

import java.time.Instant;
import java.util.UUID;

public class VehicleTelemetryEvent {

    private UUID vehicleId;
    private Double batteryOrFuelLevel;
    private Double odometerKm;
    private Double engineTempC;
    private Double lat;
    private Double lng;
    private Instant timestamp;

    public VehicleTelemetryEvent() {
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(UUID vehicleId) {
        this.vehicleId = vehicleId;
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

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final VehicleTelemetryEvent event = new VehicleTelemetryEvent();

        public Builder vehicleId(UUID vehicleId) {
            event.setVehicleId(vehicleId);
            return this;
        }

        public Builder batteryOrFuelLevel(Double batteryOrFuelLevel) {
            event.setBatteryOrFuelLevel(batteryOrFuelLevel);
            return this;
        }

        public Builder odometerKm(Double odometerKm) {
            event.setOdometerKm(odometerKm);
            return this;
        }

        public Builder engineTempC(Double engineTempC) {
            event.setEngineTempC(engineTempC);
            return this;
        }

        public Builder lat(Double lat) {
            event.setLat(lat);
            return this;
        }

        public Builder lng(Double lng) {
            event.setLng(lng);
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            event.setTimestamp(timestamp);
            return this;
        }

        public VehicleTelemetryEvent build() {
            return event;
        }
    }
}
