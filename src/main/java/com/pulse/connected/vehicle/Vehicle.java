package com.pulse.connected.vehicle;

import com.pulse.connected.auth.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 17)
    private String vin;

    @Column(nullable = false)
    private String model;

    @Column(name = "model_year", nullable = false)
    private Integer modelYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "vehicle_authorized_users",
            joinColumns = @JoinColumn(name = "vehicle_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> authorizedUsers = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "connectivity_state", nullable = false)
    private ConnectivityState connectivityState = ConnectivityState.OFFLINE;

    @Column(name = "current_software_version", nullable = false)
    private String currentSoftwareVersion = "1.0.0";

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "battery_or_fuel_level")
    private Double batteryOrFuelLevel = 100.0;

    @Column(name = "odometer_km")
    private Double odometerKm = 0.0;

    @Column(name = "last_lat")
    private Double lastLat;

    @Column(name = "last_lng")
    private Double lastLng;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Vehicle() {
    }

    public Vehicle(UUID id, String vin, String model, Integer modelYear, User owner, Set<User> authorizedUsers, ConnectivityState connectivityState, String currentSoftwareVersion, Instant lastSeenAt, Double batteryOrFuelLevel, Double odometerKm, Double lastLat, Double lastLng, Instant createdAt) {
        this.id = id;
        this.vin = vin;
        this.model = model;
        this.modelYear = modelYear;
        this.owner = owner;
        this.authorizedUsers = authorizedUsers != null ? authorizedUsers : new HashSet<>();
        this.connectivityState = connectivityState != null ? connectivityState : ConnectivityState.OFFLINE;
        this.currentSoftwareVersion = currentSoftwareVersion != null ? currentSoftwareVersion : "1.0.0";
        this.lastSeenAt = lastSeenAt;
        this.batteryOrFuelLevel = batteryOrFuelLevel != null ? batteryOrFuelLevel : 100.0;
        this.odometerKm = odometerKm != null ? odometerKm : 0.0;
        this.lastLat = lastLat;
        this.lastLng = lastLng;
        this.createdAt = createdAt;
    }

    public static VehicleBuilder builder() {
        return new VehicleBuilder();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getVin() { return vin; }
    public void setVin(String vin) { this.vin = vin; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Integer getModelYear() { return modelYear; }
    public void setModelYear(Integer modelYear) { this.modelYear = modelYear; }

    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }

    public Set<User> getAuthorizedUsers() { return authorizedUsers; }
    public void setAuthorizedUsers(Set<User> authorizedUsers) { this.authorizedUsers = authorizedUsers; }

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

    public static class VehicleBuilder {
        private UUID id;
        private String vin;
        private String model;
        private Integer modelYear;
        private User owner;
        private Set<User> authorizedUsers = new HashSet<>();
        private ConnectivityState connectivityState = ConnectivityState.OFFLINE;
        private String currentSoftwareVersion = "1.0.0";
        private Instant lastSeenAt;
        private Double batteryOrFuelLevel = 100.0;
        private Double odometerKm = 0.0;
        private Double lastLat;
        private Double lastLng;
        private Instant createdAt;

        VehicleBuilder() {}

        public VehicleBuilder id(UUID id) { this.id = id; return this; }
        public VehicleBuilder vin(String vin) { this.vin = vin; return this; }
        public VehicleBuilder model(String model) { this.model = model; return this; }
        public VehicleBuilder modelYear(Integer modelYear) { this.modelYear = modelYear; return this; }
        public VehicleBuilder owner(User owner) { this.owner = owner; return this; }
        public VehicleBuilder authorizedUsers(Set<User> authorizedUsers) { this.authorizedUsers = authorizedUsers; return this; }
        public VehicleBuilder connectivityState(ConnectivityState connectivityState) { this.connectivityState = connectivityState; return this; }
        public VehicleBuilder currentSoftwareVersion(String currentSoftwareVersion) { this.currentSoftwareVersion = currentSoftwareVersion; return this; }
        public VehicleBuilder lastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; return this; }
        public VehicleBuilder batteryOrFuelLevel(Double batteryOrFuelLevel) { this.batteryOrFuelLevel = batteryOrFuelLevel; return this; }
        public VehicleBuilder odometerKm(Double odometerKm) { this.odometerKm = odometerKm; return this; }
        public VehicleBuilder lastLat(Double lastLat) { this.lastLat = lastLat; return this; }
        public VehicleBuilder lastLng(Double lastLng) { this.lastLng = lastLng; return this; }
        public VehicleBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public Vehicle build() {
            return new Vehicle(id, vin, model, modelYear, owner, authorizedUsers, connectivityState, currentSoftwareVersion, lastSeenAt, batteryOrFuelLevel, odometerKm, lastLat, lastLng, createdAt);
        }
    }
}
