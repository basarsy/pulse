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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
        SoftwareVersion version = softwareVersionRepository.findById(request.softwareVersionId())
                .orElseThrow(() -> new ResourceNotFoundException("Software version not found with ID: " + request.softwareVersionId()));

        List<Integer> stagePercentages = request.stagePercentages();
        if (stagePercentages == null || stagePercentages.isEmpty()) {
            stagePercentages = List.of(5, 25, 100);
        }

        RolloutCampaign campaign = RolloutCampaign.builder()
                .softwareVersion(version)
                .targetModel(request.targetModel())
                .status(CampaignStatus.DRAFT)
                .currentStageIndex(0)
                .failureThresholdPercent(request.failureThresholdPercent())
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
        RolloutCampaign campaign = campaignRepository.findByIdForUpdate(campaignId)
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
        RolloutCampaign campaign = campaignRepository.findByIdForUpdate(campaignId)
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
        RolloutCampaign campaign = campaignRepository.findByIdForUpdate(campaignId)
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
        // Progress events are consumed in parallel. Lock the campaign first, and only then read the
        // update: events for one campaign are applied one after another, each seeing the previous
        // one's committed changes, so the last one of a stage reliably sees the stage finished
        // (no stall) and only it advances (no double start).
        UUID campaignId = vehicleUpdateStatusRepository.findCampaignIdById(event.updateStatusId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle update status not found with ID: " + event.updateStatusId()));
        RolloutCampaign campaign = campaignRepository.findByIdForUpdate(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Rollout campaign not found with ID: " + campaignId));
        VehicleUpdateStatus updateStatus = vehicleUpdateStatusRepository.findById(event.updateStatusId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle update status not found with ID: " + event.updateStatusId()));

        if (!UpdateStatusTransitions.isAllowed(updateStatus.getStatus(), event.status())) {
            log.info("Ignoring stale OTA progress for update [{}]: {} -> {} is not allowed",
                    updateStatus.getId(), updateStatus.getStatus(), event.status());
            return;
        }

        updateStatus.setStatus(event.status());
        updateStatus.setProgressPercent(event.progressPercent() != null ? event.progressPercent() : 0);

        if (event.status() == UpdateStatus.DOWNLOADING && updateStatus.getStartedAt() == null) {
            updateStatus.setStartedAt(Instant.now());
        }

        if (event.status() == UpdateStatus.INSTALLED) {
            updateStatus.setCompletedAt(Instant.now());
            // Update physical vehicle software version
            Vehicle vehicle = updateStatus.getVehicle();
            vehicle.setCurrentSoftwareVersion(updateStatus.getTargetVersion().getVersionLabel());
            vehicleRepository.save(vehicle);
        } else if (event.status() == UpdateStatus.FAILED || event.status() == UpdateStatus.ROLLED_BACK) {
            updateStatus.setCompletedAt(Instant.now());
            updateStatus.setErrorMessage(event.errorMessage() != null ? event.errorMessage() : "Installation failed");
        }
        if (updateStatus.isFinished()) {
            meterRegistry.counter("pulse.ota.updates.finished", "result", event.status().name()).increment();
        }

        VehicleUpdateStatus savedStatus = vehicleUpdateStatusRepository.save(updateStatus);

        // Broadcast per-vehicle WS update
        VehicleUpdateStatusResponse vehicleResponse = mapToUpdateStatusResponse(savedStatus);
        otaWebSocketService.notifyVehicleUpdateStatus(savedStatus.getVehicle().getId(), vehicleResponse);

        // Evaluate campaign stage safety & progress, on the locked instance
        evaluateCampaignStage(campaign);
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
        // order so every stage extends the previous one. Only the vehicles to instruct are loaded.
        String model = campaign.getTargetModel();
        long fleetSize = vehicleUpdateStatusRepository.countVehiclesOfModel(model);
        // Stage percentages are not validated: a share above 100% means the whole fleet.
        int targetCount = (int) Math.min(fleetSize, Math.ceil((fleetSize * activeStage.getPercentage()) / 100.0));
        String targetLabel = campaign.getSoftwareVersion().getVersionLabel();

        // Within the share: only vehicles not targeted in an earlier stage and not already on the
        // target version. If the fleet shrank since the count, there is no cutoff VIN: instruct
        // nothing now rather than fail the event (later stages count the fleet afresh).
        List<Vehicle> newlyTargeted = List.of();
        if (targetCount > 0) {
            Optional<String> lastVin = vehicleUpdateStatusRepository
                    .findVinsOfModel(model, PageRequest.of(targetCount - 1, 1)).stream().findFirst();
            if (lastVin.isPresent()) {
                newlyTargeted = vehicleUpdateStatusRepository.findVehiclesToInstruct(campaign.getId(), model, lastVin.get(), targetLabel);
            } else {
                log.warn("Campaign [{}]: fleet of {} shrank below {} while starting stage {}; nothing instructed",
                        campaign.getId(), model, targetCount, stageIdx + 1);
            }
        }

        log.info("Executing campaign [{}] stage {} ({}%): {} of {} vehicles in scope, {} newly instructed.",
                campaign.getId(), stageIdx + 1, activeStage.getPercentage(), targetCount, fleetSize, newlyTargeted.size());

        for (Vehicle vehicle : newlyTargeted) {
            VehicleUpdateStatus savedStatus = vehicleUpdateStatusRepository.save(VehicleUpdateStatus.builder()
                    .vehicle(vehicle)
                    .campaign(campaign)
                    .targetVersion(campaign.getSoftwareVersion())
                    .stageIndex(stageIdx)
                    .status(UpdateStatus.PENDING)
                    .progressPercent(0)
                    .build());

            OtaInstructionEvent instruction = new OtaInstructionEvent(
                    savedStatus.getId(),
                    vehicle.getId(),
                    campaign.getId(),
                    campaign.getSoftwareVersion().getId(),
                    campaign.getSoftwareVersion().getVersionLabel(),
                    campaign.getSoftwareVersion().getPackageUrl(),
                    campaign.getSoftwareVersion().getChecksum());

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
        int stageIdx = campaign.getCurrentStageIndex();
        CampaignProgress progress = progressOf(campaign);
        long stageTotal = progress.stageTotal(stageIdx);
        if (stageTotal == 0) {
            return;
        }

        long failedCount = progress.stageFailed(stageIdx);
        double failureRate = failedCount * 100.0 / stageTotal;

        log.info("Campaign [{}] stage {}: {}/{} failed ({}% vs threshold {}%)",
                campaign.getId(), stageIdx + 1, failedCount, stageTotal,
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

    /** One grouped query; the result answers stage evaluation and the response alike. */
    private CampaignProgress progressOf(RolloutCampaign campaign) {
        return CampaignProgress.of(vehicleUpdateStatusRepository.countByStageAndStatus(campaign.getId()));
    }

    private void advanceIfStageFinished(RolloutCampaign campaign) {
        if (campaign.getStatus() != CampaignStatus.IN_PROGRESS) {
            return;
        }
        // A stage without updates (everything in scope already targeted or up to date) is finished.
        int stageIdx = campaign.getCurrentStageIndex();
        CampaignProgress progress = progressOf(campaign);
        boolean stageFinished = progress.stageFinished(stageIdx) == progress.stageTotal(stageIdx);
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
        CampaignProgress progress = progressOf(campaign);
        long installed = progress.installed();
        long failed = progress.failed();
        long total = progress.total();

        List<RolloutStageResponse> stageResponses = campaign.getStages().stream()
                .map(s -> new RolloutStageResponse(
                        s.getId(),
                        s.getPercentage(),
                        s.getStatus(),
                        s.getStartedAt(),
                        s.getCompletedAt()))
                .collect(Collectors.toList());

        return new CampaignResponse(
                campaign.getId(),
                softwareVersionService.mapToResponse(campaign.getSoftwareVersion()),
                campaign.getTargetModel(),
                campaign.getStatus(),
                campaign.getCurrentStageIndex(),
                campaign.getFailureThresholdPercent(),
                campaign.getCreatedBy() != null ? campaign.getCreatedBy().getId() : null,
                campaign.getCreatedAt(),
                stageResponses,
                total,
                installed,
                failed);
    }

    public VehicleUpdateStatusResponse mapToUpdateStatusResponse(VehicleUpdateStatus status) {
        return new VehicleUpdateStatusResponse(
                status.getId(),
                status.getVehicle().getId(),
                status.getVehicle().getVin(),
                status.getCampaign().getId(),
                status.getTargetVersion().getId(),
                status.getTargetVersion().getVersionLabel(),
                status.getStatus(),
                status.getProgressPercent(),
                status.getStartedAt(),
                status.getCompletedAt(),
                status.getErrorMessage());
    }
}
