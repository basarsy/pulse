package com.pulse.connected.telemetry;

import com.pulse.connected.vehicle.Vehicle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "telemetry_snapshots")
public class TelemetrySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @CreationTimestamp
    @Column(name = "recorded_at")
    private Instant recordedAt;

    @Column(name = "battery_or_fuel_level")
    private Double batteryOrFuelLevel;

    @Column(name = "odometer_km")
    private Double odometerKm;

    @Column(name = "engine_temp_c")
    private Double engineTempC;

    private Double lat;
    private Double lng;

    public TelemetrySnapshot() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
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
        private final TelemetrySnapshot snapshot = new TelemetrySnapshot();

        public Builder id(UUID id) {
            snapshot.setId(id);
            return this;
        }

        public Builder vehicle(Vehicle vehicle) {
            snapshot.setVehicle(vehicle);
            return this;
        }

        public Builder recordedAt(Instant recordedAt) {
            snapshot.setRecordedAt(recordedAt);
            return this;
        }

        public Builder batteryOrFuelLevel(Double batteryOrFuelLevel) {
            snapshot.setBatteryOrFuelLevel(batteryOrFuelLevel);
            return this;
        }

        public Builder odometerKm(Double odometerKm) {
            snapshot.setOdometerKm(odometerKm);
            return this;
        }

        public Builder engineTempC(Double engineTempC) {
            snapshot.setEngineTempC(engineTempC);
            return this;
        }

        public Builder lat(Double lat) {
            snapshot.setLat(lat);
            return this;
        }

        public Builder lng(Double lng) {
            snapshot.setLng(lng);
            return this;
        }

        public TelemetrySnapshot build() {
            return snapshot;
        }
    }
}
