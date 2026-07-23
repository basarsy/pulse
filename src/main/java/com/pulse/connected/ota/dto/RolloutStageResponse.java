package com.pulse.connected.ota.dto;

import com.pulse.connected.ota.RolloutStageStatus;

import java.time.Instant;
import java.util.UUID;

public class RolloutStageResponse {

    private UUID id;
    private Integer percentage;
    private RolloutStageStatus status;
    private Instant startedAt;
    private Instant completedAt;

    public RolloutStageResponse() {}

    public RolloutStageResponse(UUID id, Integer percentage, RolloutStageStatus status, Instant startedAt, Instant completedAt) {
        this.id = id;
        this.percentage = percentage;
        this.status = status;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    public static RolloutStageResponseBuilder builder() {
        return new RolloutStageResponseBuilder();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Integer getPercentage() { return percentage; }
    public void setPercentage(Integer percentage) { this.percentage = percentage; }

    public RolloutStageStatus getStatus() { return status; }
    public void setStatus(RolloutStageStatus status) { this.status = status; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public static class RolloutStageResponseBuilder {
        private UUID id;
        private Integer percentage;
        private RolloutStageStatus status;
        private Instant startedAt;
        private Instant completedAt;

        RolloutStageResponseBuilder() {}

        public RolloutStageResponseBuilder id(UUID id) { this.id = id; return this; }
        public RolloutStageResponseBuilder percentage(Integer percentage) { this.percentage = percentage; return this; }
        public RolloutStageResponseBuilder status(RolloutStageStatus status) { this.status = status; return this; }
        public RolloutStageResponseBuilder startedAt(Instant startedAt) { this.startedAt = startedAt; return this; }
        public RolloutStageResponseBuilder completedAt(Instant completedAt) { this.completedAt = completedAt; return this; }

        public RolloutStageResponse build() {
            return new RolloutStageResponse(id, percentage, status, startedAt, completedAt);
        }
    }
}
