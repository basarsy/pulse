package com.pulse.connected.ota.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public class CreateCampaignRequest {

    @NotNull(message = "Software version ID is required")
    private UUID softwareVersionId;

    @NotBlank(message = "Target model is required")
    private String targetModel;

    private Double failureThresholdPercent = 10.0;

    private List<Integer> stagePercentages;

    public CreateCampaignRequest() {}

    public CreateCampaignRequest(UUID softwareVersionId, String targetModel, Double failureThresholdPercent, List<Integer> stagePercentages) {
        this.softwareVersionId = softwareVersionId;
        this.targetModel = targetModel;
        this.failureThresholdPercent = failureThresholdPercent != null ? failureThresholdPercent : 10.0;
        this.stagePercentages = stagePercentages;
    }

    public static CreateCampaignRequestBuilder builder() {
        return new CreateCampaignRequestBuilder();
    }

    public UUID getSoftwareVersionId() { return softwareVersionId; }
    public void setSoftwareVersionId(UUID softwareVersionId) { this.softwareVersionId = softwareVersionId; }

    public String getTargetModel() { return targetModel; }
    public void setTargetModel(String targetModel) { this.targetModel = targetModel; }

    public Double getFailureThresholdPercent() { return failureThresholdPercent; }
    public void setFailureThresholdPercent(Double failureThresholdPercent) { this.failureThresholdPercent = failureThresholdPercent; }

    public List<Integer> getStagePercentages() { return stagePercentages; }
    public void setStagePercentages(List<Integer> stagePercentages) { this.stagePercentages = stagePercentages; }

    public static class CreateCampaignRequestBuilder {
        private UUID softwareVersionId;
        private String targetModel;
        private Double failureThresholdPercent = 10.0;
        private List<Integer> stagePercentages;

        CreateCampaignRequestBuilder() {}

        public CreateCampaignRequestBuilder softwareVersionId(UUID softwareVersionId) { this.softwareVersionId = softwareVersionId; return this; }
        public CreateCampaignRequestBuilder targetModel(String targetModel) { this.targetModel = targetModel; return this; }
        public CreateCampaignRequestBuilder failureThresholdPercent(Double failureThresholdPercent) { this.failureThresholdPercent = failureThresholdPercent; return this; }
        public CreateCampaignRequestBuilder stagePercentages(List<Integer> stagePercentages) { this.stagePercentages = stagePercentages; return this; }

        public CreateCampaignRequest build() {
            return new CreateCampaignRequest(softwareVersionId, targetModel, failureThresholdPercent, stagePercentages);
        }
    }
}
