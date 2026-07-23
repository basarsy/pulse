package com.pulse.connected.ota;

import com.pulse.connected.vehicle.Vehicle;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vehicle_update_status")
public class VehicleUpdateStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private RolloutCampaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_version_id", nullable = false)
    private SoftwareVersion targetVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UpdateStatus status = UpdateStatus.PENDING;

    @Column(name = "progress_percent", nullable = false)
    private Integer progressPercent = 0;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    public VehicleUpdateStatus() {}

    public VehicleUpdateStatus(UUID id, Vehicle vehicle, RolloutCampaign campaign, SoftwareVersion targetVersion, UpdateStatus status, Integer progressPercent, Instant startedAt, Instant completedAt, String errorMessage) {
        this.id = id;
        this.vehicle = vehicle;
        this.campaign = campaign;
        this.targetVersion = targetVersion;
        this.status = status != null ? status : UpdateStatus.PENDING;
        this.progressPercent = progressPercent != null ? progressPercent : 0;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.errorMessage = errorMessage;
    }

    public static VehicleUpdateStatusBuilder builder() {
        return new VehicleUpdateStatusBuilder();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle vehicle) { this.vehicle = vehicle; }

    public RolloutCampaign getCampaign() { return campaign; }
    public void setCampaign(RolloutCampaign campaign) { this.campaign = campaign; }

    public SoftwareVersion getTargetVersion() { return targetVersion; }
    public void setTargetVersion(SoftwareVersion targetVersion) { this.targetVersion = targetVersion; }

    public UpdateStatus getStatus() { return status; }
    public void setStatus(UpdateStatus status) { this.status = status; }

    public Integer getProgressPercent() { return progressPercent; }
    public void setProgressPercent(Integer progressPercent) { this.progressPercent = progressPercent; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public static class VehicleUpdateStatusBuilder {
        private UUID id;
        private Vehicle vehicle;
        private RolloutCampaign campaign;
        private SoftwareVersion targetVersion;
        private UpdateStatus status = UpdateStatus.PENDING;
        private Integer progressPercent = 0;
        private Instant startedAt;
        private Instant completedAt;
        private String errorMessage;

        VehicleUpdateStatusBuilder() {}

        public VehicleUpdateStatusBuilder id(UUID id) { this.id = id; return this; }
        public VehicleUpdateStatusBuilder vehicle(Vehicle vehicle) { this.vehicle = vehicle; return this; }
        public VehicleUpdateStatusBuilder campaign(RolloutCampaign campaign) { this.campaign = campaign; return this; }
        public VehicleUpdateStatusBuilder targetVersion(SoftwareVersion targetVersion) { this.targetVersion = targetVersion; return this; }
        public VehicleUpdateStatusBuilder status(UpdateStatus status) { this.status = status; return this; }
        public VehicleUpdateStatusBuilder progressPercent(Integer progressPercent) { this.progressPercent = progressPercent; return this; }
        public VehicleUpdateStatusBuilder startedAt(Instant startedAt) { this.startedAt = startedAt; return this; }
        public VehicleUpdateStatusBuilder completedAt(Instant completedAt) { this.completedAt = completedAt; return this; }
        public VehicleUpdateStatusBuilder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }

        public VehicleUpdateStatus build() {
            return new VehicleUpdateStatus(id, vehicle, campaign, targetVersion, status, progressPercent, startedAt, completedAt, errorMessage);
        }
    }
}
