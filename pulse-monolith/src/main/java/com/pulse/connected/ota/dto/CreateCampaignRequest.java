package com.pulse.connected.ota.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateCampaignRequest(
        @NotNull(message = "Software version ID is required")
        UUID softwareVersionId,

        @NotBlank(message = "Target model is required")
        String targetModel,

        @DecimalMin(value = "0", message = "must be between 0 and 100")
        @DecimalMax(value = "100", message = "must be between 0 and 100")
        Double failureThresholdPercent,

        @ValidStagePercentages
        @JsonDeserialize(contentUsing = StrictIntegerDeserializer.class)
        List<Integer> stagePercentages) {

    private static final double DEFAULT_FAILURE_THRESHOLD_PERCENT = 10.0;

    public CreateCampaignRequest {
        if (failureThresholdPercent == null) {
            failureThresholdPercent = DEFAULT_FAILURE_THRESHOLD_PERCENT;
        }
    }
}
