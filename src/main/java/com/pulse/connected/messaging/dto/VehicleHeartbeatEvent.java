package com.pulse.connected.messaging.dto;

import com.pulse.connected.vehicle.ConnectivityState;

import java.time.Instant;
import java.util.UUID;

public class VehicleHeartbeatEvent {

    private UUID vehicleId;
    private String vin;
    private ConnectivityState state;
    private Instant timestamp;

    public VehicleHeartbeatEvent() {}

    public VehicleHeartbeatEvent(UUID vehicleId, String vin, ConnectivityState state, Instant timestamp) {
        this.vehicleId = vehicleId;
        this.vin = vin;
        this.state = state;
        this.timestamp = timestamp;
    }

    public static VehicleHeartbeatEventBuilder builder() {
        return new VehicleHeartbeatEventBuilder();
    }

    public UUID getVehicleId() { return vehicleId; }
    public void setVehicleId(UUID vehicleId) { this.vehicleId = vehicleId; }

    public String getVin() { return vin; }
    public void setVin(String vin) { this.vin = vin; }

    public ConnectivityState getState() { return state; }
    public void setState(ConnectivityState state) { this.state = state; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public static class VehicleHeartbeatEventBuilder {
        private UUID vehicleId;
        private String vin;
        private ConnectivityState state;
        private Instant timestamp;

        VehicleHeartbeatEventBuilder() {}

        public VehicleHeartbeatEventBuilder vehicleId(UUID vehicleId) { this.vehicleId = vehicleId; return this; }
        public VehicleHeartbeatEventBuilder vin(String vin) { this.vin = vin; return this; }
        public VehicleHeartbeatEventBuilder state(ConnectivityState state) { this.state = state; return this; }
        public VehicleHeartbeatEventBuilder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }

        public VehicleHeartbeatEvent build() {
            return new VehicleHeartbeatEvent(vehicleId, vin, state, timestamp);
        }
    }
}
