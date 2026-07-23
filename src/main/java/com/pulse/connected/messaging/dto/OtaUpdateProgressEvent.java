package com.pulse.connected.messaging.dto;

import com.pulse.connected.ota.UpdateStatus;

import java.time.Instant;
import java.util.UUID;

public class OtaUpdateProgressEvent {

    private UUID updateStatusId;
    private UUID vehicleId;
    private UUID campaignId;
    private UpdateStatus status;
    private Integer progressPercent;
    private String errorMessage;
    private Instant timestamp;

    public OtaUpdateProgressEvent() {}

    public OtaUpdateProgressEvent(UUID updateStatusId, UUID vehicleId, UUID campaignId, UpdateStatus status, Integer progressPercent, String errorMessage, Instant timestamp) {
        this.updateStatusId = updateStatusId;
        this.vehicleId = vehicleId;
        this.campaignId = campaignId;
        this.status = status;
        this.progressPercent = progressPercent;
        this.errorMessage = errorMessage;
        this.timestamp = timestamp;
    }

    public static OtaUpdateProgressEventBuilder builder() {
        return new OtaUpdateProgressEventBuilder();
    }

    public UUID getUpdateStatusId() { return updateStatusId; }
    public void setUpdateStatusId(UUID updateStatusId) { this.updateStatusId = updateStatusId; }

    public UUID getVehicleId() { return vehicleId; }
    public void setVehicleId(UUID vehicleId) { this.vehicleId = vehicleId; }

    public UUID getCampaignId() { return campaignId; }
    public void setCampaignId(UUID campaignId) { this.campaignId = campaignId; }

    public UpdateStatus getStatus() { return status; }
    public void setStatus(UpdateStatus status) { this.status = status; }

    public Integer getProgressPercent() { return progressPercent; }
    public void setProgressPercent(Integer progressPercent) { this.progressPercent = progressPercent; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public static class OtaUpdateProgressEventBuilder {
        private UUID updateStatusId;
        private UUID vehicleId;
        private UUID campaignId;
        private UpdateStatus status;
        private Integer progressPercent;
        private String errorMessage;
        private Instant timestamp;

        OtaUpdateProgressEventBuilder() {}

        public OtaUpdateProgressEventBuilder updateStatusId(UUID updateStatusId) { this.updateStatusId = updateStatusId; return this; }
        public OtaUpdateProgressEventBuilder vehicleId(UUID vehicleId) { this.vehicleId = vehicleId; return this; }
        public OtaUpdateProgressEventBuilder campaignId(UUID campaignId) { this.campaignId = campaignId; return this; }
        public OtaUpdateProgressEventBuilder status(UpdateStatus status) { this.status = status; return this; }
        public OtaUpdateProgressEventBuilder progressPercent(Integer progressPercent) { this.progressPercent = progressPercent; return this; }
        public OtaUpdateProgressEventBuilder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public OtaUpdateProgressEventBuilder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }

        public OtaUpdateProgressEvent build() {
            return new OtaUpdateProgressEvent(updateStatusId, vehicleId, campaignId, status, progressPercent, errorMessage, timestamp);
        }
    }
}
