package com.pulse.connected.telemetry.dto;

public class IngestTelemetryRequest {

    private Double batteryOrFuelLevel;
    private Double odometerKm;
    private Double engineTempC;
    private Double lat;
    private Double lng;

    public IngestTelemetryRequest() {
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
        private final IngestTelemetryRequest request = new IngestTelemetryRequest();

        public Builder batteryOrFuelLevel(Double batteryOrFuelLevel) {
            request.setBatteryOrFuelLevel(batteryOrFuelLevel);
            return this;
        }

        public Builder odometerKm(Double odometerKm) {
            request.setOdometerKm(odometerKm);
            return this;
        }

        public Builder engineTempC(Double engineTempC) {
            request.setEngineTempC(engineTempC);
            return this;
        }

        public Builder lat(Double lat) {
            request.setLat(lat);
            return this;
        }

        public Builder lng(Double lng) {
            request.setLng(lng);
            return this;
        }

        public IngestTelemetryRequest build() {
            return request;
        }
    }
}
