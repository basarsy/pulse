package com.pulse.connected.ota;

import com.pulse.connected.domain.UpdateStatus;
import com.pulse.connected.domain.CampaignStatus;
import com.pulse.connected.domain.RolloutStageStatus;

import com.pulse.connected.auth.User;
import com.pulse.connected.common.exception.ResourceNotFoundException;
import com.pulse.connected.messaging.dto.OtaInstructionEvent;
import com.pulse.connected.messaging.dto.OtaUpdateProgressEvent;
import com.pulse.connected.ota.dto.*;
import com.pulse.connected.vehicle.Vehicle;
import com.pulse.connected.vehicle.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.MeterRegistry;
import com.pulse.connected.common.AuditLogService;

@Service
public class RolloutCampaignService {

    private static final Logger log = LoggerFactory.getLogger(RolloutCampaignService.class);

    private final RolloutCampaignRepository campaignRepository;
    private final SoftwareVersionRepository softwareVersionRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleUpdateStatusRepository vehicleUpdateStatusRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OtaWebSocketService otaWebSocketService;
    private final SoftwareVersionService softwareVersionService;
    private final AuditLogService auditLogService;
    private final MeterRegistry meterRegistry;

    public RolloutCampaignService(
            RolloutCampaignRepository campaignRepository,
            SoftwareVersionRepository softwareVersionRepository,
            VehicleRepository vehicleRepository,
            VehicleUpdateStatusRepository vehicleUpdateStatusRepository,
            ApplicationEventPublisher eventPublisher,
            OtaWebSocketService otaWebSocketService,
            SoftwareVersionService softwareVersionService,
            AuditLogService auditLogService,
            MeterRegistry meterRegistry) {
        this.campaignRepository = campaignRepository;
        this.softwareVersionRepository = softwareVersionRepository;
        this.vehicleRepository = vehicleRepository;
        this.vehicleUpdateStatusRepository = vehicleUpdateStatusRepository;
        this.eventPublisher = eventPublisher;
        this.otaWebSocketService = otaWebSocketService;
        this.softwareVersionService = softwareVersionService;
        this.auditLogService = auditLogService;
        this.meterRegistry = meterRegistry;
        
        // COUNT query per scrape (previously loaded every campaign on each Prometheus scrape).
        this.meterRegistry.gauge("pulse.rollout.campaigns.active", this.campaignRepository,
            repo -> repo.countByStatus(CampaignStatus.IN_PROGRESS));
    }

    @Transactional
    public CampaignResponse createCampaign(CreateCampaignRequest request, User currentUser) {
        SoftwareVersion version = softwareVersionRepository.findById(request.getSoftwareVersionId())
                .orElseThrow(() -> new ResourceNotFoundException("Software version not found with ID: " + request.getSoftwareVersionId()));

        List<Integer> stagePercentages = request.getStagePercentages();
        if (stagePercentages == null || stagePercentages.isEmpty()) {
            stagePercentages = List.of(5, 25, 100);
        }

        RolloutCampaign campaign = RolloutCampaign.builder()
                .softwareVersion(version)
                .targetModel(request.getTargetModel())
                .status(CampaignStatus.DRAFT)
                .currentStageIndex(0)
                .failureThresholdPercent(request.getFailureThresholdPercent() != null ? request.getFailureThresholdPercent() : 10.0)
                .createdBy(currentUser)
                .build();

        List<RolloutStage> stages = new ArrayList<>();
        for (Integer pct : stagePercentages) {
            stages.add(RolloutStage.builder()
                    .campaign(campaign)
                    .percentage(pct)
                    .status(RolloutStageStatus.PENDING)
                    .build());
        }
        campaign.setStages(stages);

        RolloutCampaign saved = campaignRepository.save(campaign);
        auditLogService.log(currentUser.getId(), "CAMPAIGN_CREATED", "RolloutCampaign", saved.getId().toString(), null);
        return mapToCampaignResponse(saved);
    }

    @Transactional
    public CampaignResponse startCampaign(UUID campaignId, User actor) {
        RolloutCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Rollout campaign not found with ID: " + campaignId));

        if (campaign.getStatus() != CampaignStatus.DRAFT && campaign.getStatus() != CampaignStatus.PAUSED) {
            throw new IllegalStateException("Cannot start campaign in status: " + campaign.getStatus());
        }

        campaign.setStatus(CampaignStatus.IN_PROGRESS);
        RolloutCampaign saved = campaignRepository.save(campaign);
        
        UUID actorId = actor.getId();
        auditLogService.log(actorId, "CAMPAIGN_STARTED", "RolloutCampaign", campaignId.toString(), null);

        executeActiveStage(saved);
        return mapToCampaignResponse(saved);
    }

    @Transactional
    public CampaignResponse pauseCampaign(UUID campaignId, User actor) {
        RolloutCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Rollout campaign not found with ID: " + campaignId));

        if (campaign.getStatus() != CampaignStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot pause campaign in status: " + campaign.getStatus());
        }

        campaign.setStatus(CampaignStatus.PAUSED);
        RolloutCampaign saved = campaignRepository.save(campaign);
        
        UUID actorId = actor.getId();
        auditLogService.log(actorId, "CAMPAIGN_PAUSED", "RolloutCampaign", campaignId.toString(), null);

        CampaignResponse response = mapToCampaignResponse(saved);
        otaWebSocketService.notifyCampaignProgress(campaignId, response);
        return response;
    }

    @Transactional
    public CampaignResponse resumeCampaign(UUID campaignId, User actor) {
        return startCampaign(campaignId, actor);
    }

    @Transactional
    public CampaignResponse abortCampaign(UUID campaignId, User actor) {
        RolloutCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Rollout campaign not found with ID: " + campaignId));

        if (campaign.getStatus() == CampaignStatus.COMPLETED || campaign.getStatus() == CampaignStatus.ABORTED) {
            throw new IllegalStateException("Cannot abort campaign in status: " + campaign.getStatus());
        }

        campaign.setStatus(CampaignStatus.ABORTED);
        RolloutCampaign saved = campaignRepository.save(campaign);
        
        UUID actorId = actor.getId();
        auditLogService.log(actorId, "CAMPAIGN_ABORTED", "RolloutCampaign", campaignId.toString(), null);

        CampaignResponse response = mapToCampaignResponse(saved);
        otaWebSocketService.notifyCampaignProgress(campaignId, response);
        return response;
    }

    @Transactional
    public void handleVehicleUpdateProgress(OtaUpdateProgressEvent event) {
        VehicleUpdateStatus updateStatus = vehicleUpdateStatusRepository.findById(event.getUpdateStatusId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle update status not found with ID: " + event.getUpdateStatusId()));

        if (!UpdateStatusTransitions.isAllowed(updateStatus.getStatus(), event.getStatus())) {
            log.info("Ignoring stale OTA progress for update [{}]: {} -> {} is not allowed",
                    updateStatus.getId(), updateStatus.getStatus(), event.getStatus());
            return;
        }

        updateStatus.setStatus(event.getStatus());
        updateStatus.setProgressPercent(event.getProgressPercent() != null ? event.getProgressPercent() : 0);

        if (event.getStatus() == UpdateStatus.DOWNLOADING && updateStatus.getStartedAt() == null) {
            updateStatus.setStartedAt(Instant.now());
        }

        if (event.getStatus() == UpdateStatus.INSTALLED) {
            updateStatus.setCompletedAt(Instant.now());
            // Update physical vehicle software version
            Vehicle vehicle = updateStatus.getVehicle();
            vehicle.setCurrentSoftwareVersion(updateStatus.getTargetVersion().getVersionLabel());
            vehicleRepository.save(vehicle);
        } else if (event.getStatus() == UpdateStatus.FAILED || event.getStatus() == UpdateStatus.ROLLED_BACK) {
            updateStatus.setCompletedAt(Instant.now());
            updateStatus.setErrorMessage(event.getErrorMessage() != null ? event.getErrorMessage() : "Installation failed");
        }
        if (updateStatus.isFinished()) {
            meterRegistry.counter("pulse.ota.updates.finished", "result", event.getStatus().name()).increment();
        }

        VehicleUpdateStatus savedStatus = vehicleUpdateStatusRepository.save(updateStatus);

        // Broadcast per-vehicle WS update
        VehicleUpdateStatusResponse vehicleResponse = mapToUpdateStatusResponse(savedStatus);
        otaWebSocketService.notifyVehicleUpdateStatus(savedStatus.getVehicle().getId(), vehicleResponse);

        // Evaluate campaign stage safety & progress
        evaluateCampaignStage(savedStatus.getCampaign());
    }

    @Transactional
    public void executeActiveStage(RolloutCampaign campaign) {
        List<RolloutStage> stages = campaign.getStages();
        int stageIdx = campaign.getCurrentStageIndex();

        if (stageIdx >= stages.size()) {
            log.info("Campaign [{}] has completed all stages.", campaign.getId());
            campaign.setStatus(CampaignStatus.COMPLETED);
            campaignRepository.save(campaign);
            otaWebSocketService.notifyCampaignProgress(campaign.getId(), mapToCampaignResponse(campaign));
            return;
        }

        RolloutStage activeStage = stages.get(stageIdx);
        activeStage.setStatus(RolloutStageStatus.IN_PROGRESS);
        if (activeStage.getStartedAt() == null) {
            activeStage.setStartedAt(Instant.now());
        }

        // Stage percentages are cumulative shares of the model's fleet, taken in a stable (VIN)
        // order so every stage extends the previous one.
        List<Vehicle> fleet = vehicleRepository.findAllByModelIgnoreCaseOrderByVinAsc(campaign.getTargetModel());
        int targetCount = (int) Math.ceil((fleet.size() * activeStage.getPercentage()) / 100.0);

        Set<UUID> alreadyTargeted = vehicleUpdateStatusRepository.findAllByCampaignId(campaign.getId()).stream()
                .map(update -> update.getVehicle().getId())
                .collect(Collectors.toSet());
        String targetLabel = campaign.getSoftwareVersion().getVersionLabel();

        // Only vehicles not targeted in an earlier stage and not already on the target version.
        List<Vehicle> newlyTargeted = fleet.stream()
                .limit(targetCount)
                .filter(v -> !alreadyTargeted.contains(v.getId()))
                .filter(v -> !targetLabel.equals(v.getCurrentSoftwareVersion()))
                .toList();

        log.info("Executing campaign [{}] stage {} ({}%): {} of {} vehicles in scope, {} newly instructed.",
                campaign.getId(), stageIdx + 1, activeStage.getPercentage(), targetCount, fleet.size(), newlyTargeted.size());

        for (Vehicle vehicle : newlyTargeted) {
            VehicleUpdateStatus savedStatus = vehicleUpdateStatusRepository.save(VehicleUpdateStatus.builder()
                    .vehicle(vehicle)
                    .campaign(campaign)
                    .targetVersion(campaign.getSoftwareVersion())
                    .stageIndex(stageIdx)
                    .status(UpdateStatus.PENDING)
                    .progressPercent(0)
                    .build());

            OtaInstructionEvent instruction = OtaInstructionEvent.builder()
                    .updateStatusId(savedStatus.getId())
                    .vehicleId(vehicle.getId())
                    .campaignId(campaign.getId())
                    .targetVersionId(campaign.getSoftwareVersion().getId())
                    .targetVersionLabel(campaign.getSoftwareVersion().getVersionLabel())
                    .packageUrl(campaign.getSoftwareVersion().getPackageUrl())
                    .checksum(campaign.getSoftwareVersion().getChecksum())
                    .build();

            // Recorded in the outbox with this transaction; sent to Kafka after commit (OtaKafkaRelay).
            eventPublisher.publishEvent(instruction);
        }

        campaignRepository.save(campaign);
        otaWebSocketService.notifyCampaignProgress(campaign.getId(), mapToCampaignResponse(campaign));

        if (newlyTargeted.isEmpty()) {
            // Nothing new to instruct (vehicles in scope were targeted earlier or are already on
            // the target version). Without in-flight updates no progress event would ever
            // complete this stage, so check right away.
            advanceIfStageFinished(campaign);
        }
    }

    /**
     * Pauses the campaign if the failure rate of the <em>current stage</em> exceeds the threshold,
     * otherwise advances once every vehicle of the stage has finished. Evaluating per stage keeps
     * successes from earlier stages from masking a failing stage.
     */
    private void evaluateCampaignStage(RolloutCampaign campaign) {
        if (campaign.getStatus() != CampaignStatus.IN_PROGRESS) {
            return;
        }
        List<VehicleUpdateStatus> stageUpdates = updatesInCurrentStage(campaign);
        if (stageUpdates.isEmpty()) {
            return;
        }

        long failedCount = stageUpdates.stream().filter(VehicleUpdateStatus::isFailed).count();
        double failureRate = failedCount * 100.0 / stageUpdates.size();

        log.info("Campaign [{}] stage {}: {}/{} failed ({}% vs threshold {}%)",
                campaign.getId(), campaign.getCurrentStageIndex() + 1, failedCount, stageUpdates.size(),
                String.format("%.2f", failureRate), campaign.getFailureThresholdPercent());

        if (failureRate > campaign.getFailureThresholdPercent()) {
            log.warn("AUTO-PAUSING campaign [{}]: stage failure rate {}% exceeded threshold {}%",
                    campaign.getId(), String.format("%.2f", failureRate), campaign.getFailureThresholdPercent());

            campaign.setStatus(CampaignStatus.PAUSED);
            campaignRepository.save(campaign);
            otaWebSocketService.notifyCampaignProgress(campaign.getId(), mapToCampaignResponse(campaign));
            return;
        }

        advanceIfStageFinished(campaign);
    }

    private List<VehicleUpdateStatus> updatesInCurrentStage(RolloutCampaign campaign) {
        int stageIdx = campaign.getCurrentStageIndex();
        return vehicleUpdateStatusRepository.findAllByCampaignId(campaign.getId()).stream()
                .filter(update -> update.getStageIndex() == stageIdx)
                .toList();
    }

    private void advanceIfStageFinished(RolloutCampaign campaign) {
        if (campaign.getStatus() != CampaignStatus.IN_PROGRESS) {
            return;
        }
        boolean stageFinished = updatesInCurrentStage(campaign).stream().allMatch(VehicleUpdateStatus::isFinished);
        if (stageFinished) {
            RolloutStage currentStage = campaign.getStages().get(campaign.getCurrentStageIndex());
            currentStage.setStatus(RolloutStageStatus.COMPLETED);
            currentStage.setCompletedAt(Instant.now());

            int nextStageIdx = campaign.getCurrentStageIndex() + 1;
            if (nextStageIdx < campaign.getStages().size()) {
                campaign.setCurrentStageIndex(nextStageIdx);
                log.info("Stage {} completed. Auto-advancing campaign [{}] to stage {} ({}%)",
                        campaign.getCurrentStageIndex(), campaign.getId(), nextStageIdx + 1, campaign.getStages().get(nextStageIdx).getPercentage());
                executeActiveStage(campaign);
            } else {
                log.info("All stages completed for campaign [{}]!", campaign.getId());
                campaign.setStatus(CampaignStatus.COMPLETED);
                campaignRepository.save(campaign);
                
                UUID actorId = campaign.getCreatedBy() != null ? campaign.getCreatedBy().getId() : null;
                auditLogService.log(actorId, "CAMPAIGN_COMPLETED", "RolloutCampaign", campaign.getId().toString(), null);
                
                otaWebSocketService.notifyCampaignProgress(campaign.getId(), mapToCampaignResponse(campaign));
            }
        }
    }

    @Transactional(readOnly = true)
    public List<CampaignResponse> listCampaigns() {
        return campaignRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToCampaignResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CampaignResponse getCampaignDetails(UUID campaignId) {
        RolloutCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Rollout campaign not found with ID: " + campaignId));
        return mapToCampaignResponse(campaign);
    }

    @Transactional(readOnly = true)
    public List<VehicleUpdateStatusResponse> getCampaignVehicleStatuses(UUID campaignId) {
        return vehicleUpdateStatusRepository.findAllByCampaignId(campaignId).stream()
                .map(this::mapToUpdateStatusResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<VehicleUpdateStatusResponse> getVehicleUpdateHistory(UUID vehicleId) {
        return vehicleUpdateStatusRepository.findAllByVehicleIdOrderByStartedAtDesc(vehicleId).stream()
                .map(this::mapToUpdateStatusResponse)
                .collect(Collectors.toList());
    }

    public CampaignResponse mapToCampaignResponse(RolloutCampaign campaign) {
        long installed = vehicleUpdateStatusRepository.countByCampaignIdAndStatus(campaign.getId(), UpdateStatus.INSTALLED);
        long failed = vehicleUpdateStatusRepository.countByCampaignIdAndStatus(campaign.getId(), UpdateStatus.FAILED)
                + vehicleUpdateStatusRepository.countByCampaignIdAndStatus(campaign.getId(), UpdateStatus.ROLLED_BACK);
        long total = vehicleUpdateStatusRepository.countTotalByCampaignId(campaign.getId());

        List<RolloutStageResponse> stageResponses = campaign.getStages().stream()
                .map(s -> RolloutStageResponse.builder()
                        .id(s.getId())
                        .percentage(s.getPercentage())
                        .status(s.getStatus())
                        .startedAt(s.getStartedAt())
                        .completedAt(s.getCompletedAt())
                        .build())
                .collect(Collectors.toList());

        return CampaignResponse.builder()
                .id(campaign.getId())
                .softwareVersion(softwareVersionService.mapToResponse(campaign.getSoftwareVersion()))
                .targetModel(campaign.getTargetModel())
                .status(campaign.getStatus())
                .currentStageIndex(campaign.getCurrentStageIndex())
                .failureThresholdPercent(campaign.getFailureThresholdPercent())
                .createdByUserId(campaign.getCreatedBy() != null ? campaign.getCreatedBy().getId() : null)
                .createdAt(campaign.getCreatedAt())
                .stages(stageResponses)
                .totalVehiclesTargeted(total)
                .vehiclesInstalled(installed)
                .vehiclesFailed(failed)
                .build();
    }

    public VehicleUpdateStatusResponse mapToUpdateStatusResponse(VehicleUpdateStatus status) {
        return VehicleUpdateStatusResponse.builder()
                .id(status.getId())
                .vehicleId(status.getVehicle().getId())
                .vin(status.getVehicle().getVin())
                .campaignId(status.getCampaign().getId())
                .targetVersionId(status.getTargetVersion().getId())
                .targetVersionLabel(status.getTargetVersion().getVersionLabel())
                .status(status.getStatus())
                .progressPercent(status.getProgressPercent())
                .startedAt(status.getStartedAt())
                .completedAt(status.getCompletedAt())
                .errorMessage(status.getErrorMessage())
                .build();
    }
}
