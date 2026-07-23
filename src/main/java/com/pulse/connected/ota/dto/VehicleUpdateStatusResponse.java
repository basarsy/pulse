package com.pulse.connected.ota.dto;

import com.pulse.connected.ota.UpdateStatus;

import java.time.Instant;
import java.util.UUID;

public class VehicleUpdateStatusResponse {

    private UUID id;
    private UUID vehicleId;
    private String vin;
    private UUID campaignId;
    private UUID targetVersionId;
    private String targetVersionLabel;
    private UpdateStatus status;
    private Integer progressPercent;
    private Instant startedAt;
    private Instant completedAt;
    private String errorMessage;

    public VehicleUpdateStatusResponse() {}

    public VehicleUpdateStatusResponse(UUID id, UUID vehicleId, String vin, UUID campaignId, UUID targetVersionId, String targetVersionLabel, UpdateStatus status, Integer progressPercent, Instant startedAt, Instant completedAt, String errorMessage) {
        this.id = id;
        this.vehicleId = vehicleId;
        this.vin = vin;
        this.campaignId = campaignId;
        this.targetVersionId = targetVersionId;
        this.targetVersionLabel = targetVersionLabel;
        this.status = status;
        this.progressPercent = progressPercent;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.errorMessage = errorMessage;
    }

    public static VehicleUpdateStatusResponseBuilder builder() {
        return new VehicleUpdateStatusResponseBuilder();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getVehicleId() { return vehicleId; }
    public void setVehicleId(UUID vehicleId) { this.vehicleId = vehicleId; }

    public String getVin() { return vin; }
    public void setVin(String vin) { this.vin = vin; }

    public UUID getCampaignId() { return campaignId; }
    public void setCampaignId(UUID campaignId) { this.campaignId = campaignId; }

    public UUID getTargetVersionId() { return targetVersionId; }
    public void setTargetVersionId(UUID targetVersionId) { this.targetVersionId = targetVersionId; }

    public String getTargetVersionLabel() { return targetVersionLabel; }
    public void setTargetVersionLabel(String targetVersionLabel) { this.targetVersionLabel = targetVersionLabel; }

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

    public static class VehicleUpdateStatusResponseBuilder {
        private UUID id;
        private UUID vehicleId;
        private String vin;
        private UUID campaignId;
        private UUID targetVersionId;
        private String targetVersionLabel;
        private UpdateStatus status;
        private Integer progressPercent;
        private Instant startedAt;
        private Instant completedAt;
        private String errorMessage;

        VehicleUpdateStatusResponseBuilder() {}

        public VehicleUpdateStatusResponseBuilder id(UUID id) { this.id = id; return this; }
        public VehicleUpdateStatusResponseBuilder vehicleId(UUID vehicleId) { this.vehicleId = vehicleId; return this; }
        public VehicleUpdateStatusResponseBuilder vin(String vin) { this.vin = vin; return this; }
        public VehicleUpdateStatusResponseBuilder campaignId(UUID campaignId) { this.campaignId = campaignId; return this; }
        public VehicleUpdateStatusResponseBuilder targetVersionId(UUID targetVersionId) { this.targetVersionId = targetVersionId; return this; }
        public VehicleUpdateStatusResponseBuilder targetVersionLabel(String targetVersionLabel) { this.targetVersionLabel = targetVersionLabel; return this; }
        public VehicleUpdateStatusResponseBuilder status(UpdateStatus status) { this.status = status; return this; }
        public VehicleUpdateStatusResponseBuilder progressPercent(Integer progressPercent) { this.progressPercent = progressPercent; return this; }
        public VehicleUpdateStatusResponseBuilder startedAt(Instant startedAt) { this.startedAt = startedAt; return this; }
        public VehicleUpdateStatusResponseBuilder completedAt(Instant completedAt) { this.completedAt = completedAt; return this; }
        public VehicleUpdateStatusResponseBuilder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }

        public VehicleUpdateStatusResponse build() {
            return new VehicleUpdateStatusResponse(id, vehicleId, vin, campaignId, targetVersionId, targetVersionLabel, status, progressPercent, startedAt, completedAt, errorMessage);
        }
    }
}
