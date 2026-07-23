package com.pulse.connected.ota;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rollout_stages")
public class RolloutStage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private RolloutCampaign campaign;

    @Column(nullable = false)
    private Integer percentage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RolloutStageStatus status = RolloutStageStatus.PENDING;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public RolloutStage() {}

    public RolloutStage(UUID id, RolloutCampaign campaign, Integer percentage, RolloutStageStatus status, Instant startedAt, Instant completedAt) {
        this.id = id;
        this.campaign = campaign;
        this.percentage = percentage;
        this.status = status != null ? status : RolloutStageStatus.PENDING;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    public static RolloutStageBuilder builder() {
        return new RolloutStageBuilder();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public RolloutCampaign getCampaign() { return campaign; }
    public void setCampaign(RolloutCampaign campaign) { this.campaign = campaign; }

    public Integer getPercentage() { return percentage; }
    public void setPercentage(Integer percentage) { this.percentage = percentage; }

    public RolloutStageStatus getStatus() { return status; }
    public void setStatus(RolloutStageStatus status) { this.status = status; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public static class RolloutStageBuilder {
        private UUID id;
        private RolloutCampaign campaign;
        private Integer percentage;
        private RolloutStageStatus status = RolloutStageStatus.PENDING;
        private Instant startedAt;
        private Instant completedAt;

        RolloutStageBuilder() {}

        public RolloutStageBuilder id(UUID id) { this.id = id; return this; }
        public RolloutStageBuilder campaign(RolloutCampaign campaign) { this.campaign = campaign; return this; }
        public RolloutStageBuilder percentage(Integer percentage) { this.percentage = percentage; return this; }
        public RolloutStageBuilder status(RolloutStageStatus status) { this.status = status; return this; }
        public RolloutStageBuilder startedAt(Instant startedAt) { this.startedAt = startedAt; return this; }
        public RolloutStageBuilder completedAt(Instant completedAt) { this.completedAt = completedAt; return this; }

        public RolloutStage build() {
            return new RolloutStage(id, campaign, percentage, status, startedAt, completedAt);
        }
    }
}
