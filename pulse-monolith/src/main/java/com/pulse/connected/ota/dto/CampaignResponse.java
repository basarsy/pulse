package com.pulse.connected.ota.dto;

import com.pulse.connected.domain.CampaignStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CampaignResponse(
        UUID id,
        SoftwareVersionResponse softwareVersion,
        String targetModel,
        CampaignStatus status,
        Integer currentStageIndex,
        Double failureThresholdPercent,
        UUID createdByUserId,
        Instant createdAt,
        List<RolloutStageResponse> stages,
        long totalVehiclesTargeted,
        long vehiclesInstalled,
        long vehiclesFailed,
        long vehiclesTimedOut,
        long version) {
}
