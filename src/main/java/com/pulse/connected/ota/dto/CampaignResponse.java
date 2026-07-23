package com.pulse.connected.ota.dto;

import com.pulse.connected.ota.CampaignStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class CampaignResponse {

    private UUID id;
    private SoftwareVersionResponse softwareVersion;
    private String targetModel;
    private CampaignStatus status;
    private Integer currentStageIndex;
    private Double failureThresholdPercent;
    private UUID createdByUserId;
    private Instant createdAt;
    private List<RolloutStageResponse> stages;
    private long totalVehiclesTargeted;
    private long vehiclesInstalled;
    private long vehiclesFailed;

    public CampaignResponse() {}

    public CampaignResponse(UUID id, SoftwareVersionResponse softwareVersion, String targetModel, CampaignStatus status, Integer currentStageIndex, Double failureThresholdPercent, UUID createdByUserId, Instant createdAt, List<RolloutStageResponse> stages, long totalVehiclesTargeted, long vehiclesInstalled, long vehiclesFailed) {
        this.id = id;
        this.softwareVersion = softwareVersion;
        this.targetModel = targetModel;
        this.status = status;
        this.currentStageIndex = currentStageIndex;
        this.failureThresholdPercent = failureThresholdPercent;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
        this.stages = stages;
        this.totalVehiclesTargeted = totalVehiclesTargeted;
        this.vehiclesInstalled = vehiclesInstalled;
        this.vehiclesFailed = vehiclesFailed;
    }

    public static CampaignResponseBuilder builder() {
        return new CampaignResponseBuilder();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public SoftwareVersionResponse getSoftwareVersion() { return softwareVersion; }
    public void setSoftwareVersion(SoftwareVersionResponse softwareVersion) { this.softwareVersion = softwareVersion; }

    public String getTargetModel() { return targetModel; }
    public void setTargetModel(String targetModel) { this.targetModel = targetModel; }

    public CampaignStatus getStatus() { return status; }
    public void setStatus(CampaignStatus status) { this.status = status; }

    public Integer getCurrentStageIndex() { return currentStageIndex; }
    public void setCurrentStageIndex(Integer currentStageIndex) { this.currentStageIndex = currentStageIndex; }

    public Double getFailureThresholdPercent() { return failureThresholdPercent; }
    public void setFailureThresholdPercent(Double failureThresholdPercent) { this.failureThresholdPercent = failureThresholdPercent; }

    public UUID getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(UUID createdByUserId) { this.createdByUserId = createdByUserId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public List<RolloutStageResponse> getStages() { return stages; }
    public void setStages(List<RolloutStageResponse> stages) { this.stages = stages; }

    public long getTotalVehiclesTargeted() { return totalVehiclesTargeted; }
    public void setTotalVehiclesTargeted(long totalVehiclesTargeted) { this.totalVehiclesTargeted = totalVehiclesTargeted; }

    public long getVehiclesInstalled() { return vehiclesInstalled; }
    public void setVehiclesInstalled(long vehiclesInstalled) { this.vehiclesInstalled = vehiclesInstalled; }

    public long getVehiclesFailed() { return vehiclesFailed; }
    public void setVehiclesFailed(long vehiclesFailed) { this.vehiclesFailed = vehiclesFailed; }

    public static class CampaignResponseBuilder {
        private UUID id;
        private SoftwareVersionResponse softwareVersion;
        private String targetModel;
        private CampaignStatus status;
        private Integer currentStageIndex;
        private Double failureThresholdPercent;
        private UUID createdByUserId;
        private Instant createdAt;
        private List<RolloutStageResponse> stages;
        private long totalVehiclesTargeted;
        private long vehiclesInstalled;
        private long vehiclesFailed;

        CampaignResponseBuilder() {}

        public CampaignResponseBuilder id(UUID id) { this.id = id; return this; }
        public CampaignResponseBuilder softwareVersion(SoftwareVersionResponse softwareVersion) { this.softwareVersion = softwareVersion; return this; }
        public CampaignResponseBuilder targetModel(String targetModel) { this.targetModel = targetModel; return this; }
        public CampaignResponseBuilder status(CampaignStatus status) { this.status = status; return this; }
        public CampaignResponseBuilder currentStageIndex(Integer currentStageIndex) { this.currentStageIndex = currentStageIndex; return this; }
        public CampaignResponseBuilder failureThresholdPercent(Double failureThresholdPercent) { this.failureThresholdPercent = failureThresholdPercent; return this; }
        public CampaignResponseBuilder createdByUserId(UUID createdByUserId) { this.createdByUserId = createdByUserId; return this; }
        public CampaignResponseBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public CampaignResponseBuilder stages(List<RolloutStageResponse> stages) { this.stages = stages; return this; }
        public CampaignResponseBuilder totalVehiclesTargeted(long totalVehiclesTargeted) { this.totalVehiclesTargeted = totalVehiclesTargeted; return this; }
        public CampaignResponseBuilder vehiclesInstalled(long vehiclesInstalled) { this.vehiclesInstalled = vehiclesInstalled; return this; }
        public CampaignResponseBuilder vehiclesFailed(long vehiclesFailed) { this.vehiclesFailed = vehiclesFailed; return this; }

        public CampaignResponse build() {
            return new CampaignResponse(id, softwareVersion, targetModel, status, currentStageIndex, failureThresholdPercent, createdByUserId, createdAt, stages, totalVehiclesTargeted, vehiclesInstalled, vehiclesFailed);
        }
    }
}
