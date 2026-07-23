package com.pulse.connected.messaging.dto;

import java.util.UUID;

public class OtaInstructionEvent {

    private UUID updateStatusId;
    private UUID vehicleId;
    private UUID campaignId;
    private UUID targetVersionId;
    private String targetVersionLabel;
    private String packageUrl;
    private String checksum;

    public OtaInstructionEvent() {}

    public OtaInstructionEvent(UUID updateStatusId, UUID vehicleId, UUID campaignId, UUID targetVersionId, String targetVersionLabel, String packageUrl, String checksum) {
        this.updateStatusId = updateStatusId;
        this.vehicleId = vehicleId;
        this.campaignId = campaignId;
        this.targetVersionId = targetVersionId;
        this.targetVersionLabel = targetVersionLabel;
        this.packageUrl = packageUrl;
        this.checksum = checksum;
    }

    public static OtaInstructionEventBuilder builder() {
        return new OtaInstructionEventBuilder();
    }

    public UUID getUpdateStatusId() { return updateStatusId; }
    public void setUpdateStatusId(UUID updateStatusId) { this.updateStatusId = updateStatusId; }

    public UUID getVehicleId() { return vehicleId; }
    public void setVehicleId(UUID vehicleId) { this.vehicleId = vehicleId; }

    public UUID getCampaignId() { return campaignId; }
    public void setCampaignId(UUID campaignId) { this.campaignId = campaignId; }

    public UUID getTargetVersionId() { return targetVersionId; }
    public void setTargetVersionId(UUID targetVersionId) { this.targetVersionId = targetVersionId; }

    public String getTargetVersionLabel() { return targetVersionLabel; }
    public void setTargetVersionLabel(String targetVersionLabel) { this.targetVersionLabel = targetVersionLabel; }

    public String getPackageUrl() { return packageUrl; }
    public void setPackageUrl(String packageUrl) { this.packageUrl = packageUrl; }

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }

    public static class OtaInstructionEventBuilder {
        private UUID updateStatusId;
        private UUID vehicleId;
        private UUID campaignId;
        private UUID targetVersionId;
        private String targetVersionLabel;
        private String packageUrl;
        private String checksum;

        OtaInstructionEventBuilder() {}

        public OtaInstructionEventBuilder updateStatusId(UUID updateStatusId) { this.updateStatusId = updateStatusId; return this; }
        public OtaInstructionEventBuilder vehicleId(UUID vehicleId) { this.vehicleId = vehicleId; return this; }
        public OtaInstructionEventBuilder campaignId(UUID campaignId) { this.campaignId = campaignId; return this; }
        public OtaInstructionEventBuilder targetVersionId(UUID targetVersionId) { this.targetVersionId = targetVersionId; return this; }
        public OtaInstructionEventBuilder targetVersionLabel(String targetVersionLabel) { this.targetVersionLabel = targetVersionLabel; return this; }
        public OtaInstructionEventBuilder packageUrl(String packageUrl) { this.packageUrl = packageUrl; return this; }
        public OtaInstructionEventBuilder checksum(String checksum) { this.checksum = checksum; return this; }

        public OtaInstructionEvent build() {
            return new OtaInstructionEvent(updateStatusId, vehicleId, campaignId, targetVersionId, targetVersionLabel, packageUrl, checksum);
        }
    }
}
