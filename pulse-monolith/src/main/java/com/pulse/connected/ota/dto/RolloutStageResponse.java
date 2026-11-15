package com.pulse.connected.ota.dto;

import com.pulse.connected.domain.RolloutStageStatus;

import java.time.Instant;
import java.util.UUID;

public record RolloutStageResponse(
        UUID id,
        Integer percentage,
        RolloutStageStatus status,
        Instant startedAt,
        Instant completedAt) {
}
