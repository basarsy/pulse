package com.pulse.connected.ota;

import com.pulse.connected.auth.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "rollout_campaigns")
public class RolloutCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "software_version_id", nullable = false)
    private SoftwareVersion softwareVersion;

    @Column(name = "target_model", nullable = false, length = 100)
    private String targetModel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignStatus status = CampaignStatus.DRAFT;

    @Column(name = "current_stage_index", nullable = false)
    private Integer currentStageIndex = 0;

    @Column(name = "failure_threshold_percent", nullable = false)
    private Double failureThresholdPercent = 10.0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("percentage ASC")
    private List<RolloutStage> stages = new ArrayList<>();

    public RolloutCampaign() {}

    public RolloutCampaign(UUID id, SoftwareVersion softwareVersion, String targetModel, CampaignStatus status, Integer currentStageIndex, Double failureThresholdPercent, User createdBy, Instant createdAt, List<RolloutStage> stages) {
        this.id = id;
        this.softwareVersion = softwareVersion;
        this.targetModel = targetModel;
        this.status = status != null ? status : CampaignStatus.DRAFT;
        this.currentStageIndex = currentStageIndex != null ? currentStageIndex : 0;
        this.failureThresholdPercent = failureThresholdPercent != null ? failureThresholdPercent : 10.0;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.stages = stages != null ? stages : new ArrayList<>();
    }

    public static RolloutCampaignBuilder builder() {
        return new RolloutCampaignBuilder();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public SoftwareVersion getSoftwareVersion() { return softwareVersion; }
    public void setSoftwareVersion(SoftwareVersion softwareVersion) { this.softwareVersion = softwareVersion; }

    public String getTargetModel() { return targetModel; }
    public void setTargetModel(String targetModel) { this.targetModel = targetModel; }

    public CampaignStatus getStatus() { return status; }
    public void setStatus(CampaignStatus status) { this.status = status; }

    public Integer getCurrentStageIndex() { return currentStageIndex; }
    public void setCurrentStageIndex(Integer currentStageIndex) { this.currentStageIndex = currentStageIndex; }

    public Double getFailureThresholdPercent() { return failureThresholdPercent; }
    public void setFailureThresholdPercent(Double failureThresholdPercent) { this.failureThresholdPercent = failureThresholdPercent; }

    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public List<RolloutStage> getStages() { return stages; }
    public void setStages(List<RolloutStage> stages) { this.stages = stages; }

    public static class RolloutCampaignBuilder {
        private UUID id;
        private SoftwareVersion softwareVersion;
        private String targetModel;
        private CampaignStatus status = CampaignStatus.DRAFT;
        private Integer currentStageIndex = 0;
        private Double failureThresholdPercent = 10.0;
        private User createdBy;
        private Instant createdAt;
        private List<RolloutStage> stages = new ArrayList<>();

        RolloutCampaignBuilder() {}

        public RolloutCampaignBuilder id(UUID id) { this.id = id; return this; }
        public RolloutCampaignBuilder softwareVersion(SoftwareVersion softwareVersion) { this.softwareVersion = softwareVersion; return this; }
        public RolloutCampaignBuilder targetModel(String targetModel) { this.targetModel = targetModel; return this; }
        public RolloutCampaignBuilder status(CampaignStatus status) { this.status = status; return this; }
        public RolloutCampaignBuilder currentStageIndex(Integer currentStageIndex) { this.currentStageIndex = currentStageIndex; return this; }
        public RolloutCampaignBuilder failureThresholdPercent(Double failureThresholdPercent) { this.failureThresholdPercent = failureThresholdPercent; return this; }
        public RolloutCampaignBuilder createdBy(User createdBy) { this.createdBy = createdBy; return this; }
        public RolloutCampaignBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public RolloutCampaignBuilder stages(List<RolloutStage> stages) { this.stages = stages; return this; }

        public RolloutCampaign build() {
            return new RolloutCampaign(id, softwareVersion, targetModel, status, currentStageIndex, failureThresholdPercent, createdBy, createdAt, stages);
        }
    }
}
