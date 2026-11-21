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
import jakarta.persistence.EntityManager;
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
    private final EntityManager entityManager;

    public RolloutCampaignService(
            RolloutCampaignRepository campaignRepository,
            SoftwareVersionRepository softwareVersionRepository,
            VehicleRepository vehicleRepository,
            VehicleUpdateStatusRepository vehicleUpdateStatusRepository,
            ApplicationEventPublisher eventPublisher,
            OtaWebSocketService otaWebSocketService,
            SoftwareVersionService softwareVersionService,
            AuditLogService auditLogService,
            MeterRegistry meterRegistry,
            EntityManager entityManager) {
        this.campaignRepository = campaignRepository;
        this.softwareVersionRepository = softwareVersionRepository;
        this.vehicleRepository = vehicleRepository;
        this.vehicleUpdateStatusRepository = vehicleUpdateStatusRepository;
        this.eventPublisher = eventPublisher;
        this.otaWebSocketService = otaWebSocketService;
        this.softwareVersionService = softwareVersionService;
        this.auditLogService = auditLogService;
        this.meterRegistry = meterRegistry;
        this.entityManager = entityManager;
        
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

        return publishProgress(saved);
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

        return publishProgress(saved);
    }

    @Transactional
    public void handleVehicleUpdateProgress(OtaUpdateProgressEvent event) {
        LockedUpdate locked = lockCampaignAndLoad(event.updateStatusId());
        VehicleUpdateStatus updateStatus = locked.update();

        if (!UpdateStatusTransitions.isAllowed(updateStatus.getStatus(), event.status())) {
            if (updateStatus.getStatus() == UpdateStatus.FAILED && event.status() == UpdateStatus.INSTALLED) {
                recordLateInstall(updateStatus);
                return;
            }
            log.info("Ignoring stale OTA progress for update [{}]: {} -> {} is not allowed",
                    updateStatus.getId(), updateStatus.getStatus(), event.status());
            return;
        }

        applyProgress(locked.campaign(), updateStatus, event.status(),
                event.progressPercent() != null ? event.progressPercent() : 0, event.errorMessage());
    }

    /**
     * Fails an update whose vehicle has reported nothing since {@code cutoff} (offline, or lost the
     * instruction), so its stage can finish: without it the stage would wait for that vehicle
     * forever. It counts as a failure, so many silent vehicles pause the rollout like failed installs.
     *
     * <p>Re-checked under the campaign lock: progress that arrived after the sweep picked the update
     * keeps it running. Returns whether the update was failed.
     */
    @Transactional
    public boolean failIfStalled(UUID updateStatusId, Instant cutoff, String reason) {
        LockedUpdate locked = lockCampaignAndLoad(updateStatusId);
        VehicleUpdateStatus updateStatus = locked.update();
        CampaignStatus campaignStatus = locked.campaign().getStatus();
        boolean campaignRunning = campaignStatus == CampaignStatus.IN_PROGRESS || campaignStatus == CampaignStatus.PAUSED;
        if (updateStatus.isFinished() || !campaignRunning || !updateStatus.getLastProgressAt().isBefore(cutoff)) {
            return false;
        }

        log.warn("OTA update [{}] of vehicle [{}] timed out in {}: {}", updateStatus.getId(),
                updateStatus.getVehicle().getId(), updateStatus.getStatus(), reason);
        meterRegistry.counter("pulse.ota.updates.timed_out").increment();
        applyProgress(locked.campaign(), updateStatus, UpdateStatus.FAILED, updateStatus.getProgressPercent(), reason);
        return true;
    }

    /**
     * The vehicle installed an update already counted as failed (typically timed out while the car
     * was parked, then it resumed). The update stays FAILED, so the stage's counts and decisions
     * don't change after the fact, but the vehicle really runs the new version now: record it, or
     * the dashboard shows the old one and the next rollout instructs this vehicle again.
     */
    private void recordLateInstall(VehicleUpdateStatus updateStatus) {
        String label = updateStatus.getTargetVersion().getVersionLabel();
        log.warn("Vehicle [{}] installed {} after its update [{}] was marked FAILED; recording the version only",
                updateStatus.getVehicle().getId(), label, updateStatus.getId());
        vehicleRepository.updateSoftwareVersion(updateStatus.getVehicle().getId(), label);
        meterRegistry.counter("pulse.ota.updates.late_installs").increment();
    }

    private record LockedUpdate(RolloutCampaign campaign, VehicleUpdateStatus update) {
    }

    /**
     * Progress events are consumed in parallel. Lock the campaign first, and only then read the
     * update: changes for one campaign are applied one after another, each seeing the previous one's
     * committed changes, so the last one of a stage reliably sees the stage finished (no stall) and
     * only it advances (no double start).
     */
    private LockedUpdate lockCampaignAndLoad(UUID updateStatusId) {
        UUID campaignId = vehicleUpdateStatusRepository.findCampaignIdById(updateStatusId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle update status not found with ID: " + updateStatusId));
        RolloutCampaign campaign = campaignRepository.findByIdForUpdate(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Rollout campaign not found with ID: " + campaignId));
        VehicleUpdateStatus updateStatus = vehicleUpdateStatusRepository.findById(updateStatusId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle update status not found with ID: " + updateStatusId));
        return new LockedUpdate(campaign, updateStatus);
    }

    /** Applies an allowed status change to an update of the locked campaign, then evaluates its stage. */
    private void applyProgress(RolloutCampaign campaign, VehicleUpdateStatus updateStatus, UpdateStatus status,
                               int progressPercent, String errorMessage) {
        Instant now = Instant.now();
        updateStatus.setStatus(status);
        updateStatus.setProgressPercent(progressPercent);
        updateStatus.setLastProgressAt(now);

        if (status == UpdateStatus.DOWNLOADING && updateStatus.getStartedAt() == null) {
            updateStatus.setStartedAt(now);
        }

        if (status == UpdateStatus.INSTALLED) {
            updateStatus.setCompletedAt(now);
            // Only the version column: heartbeats and telemetry write the same row concurrently, and
            // saving the whole loaded vehicle could revert their changes (or they this one).
            vehicleRepository.updateSoftwareVersion(updateStatus.getVehicle().getId(),
                    updateStatus.getTargetVersion().getVersionLabel());
        } else if (status == UpdateStatus.FAILED || status == UpdateStatus.ROLLED_BACK) {
            updateStatus.setCompletedAt(now);
            updateStatus.setErrorMessage(errorMessage != null ? errorMessage : "Installation failed");
        }
        if (updateStatus.isFinished()) {
            meterRegistry.counter("pulse.ota.updates.finished", "result", status.name()).increment();
        }

        // Flushed, so the response carries the version this transaction commits.
        VehicleUpdateStatus savedStatus = vehicleUpdateStatusRepository.saveAndFlush(updateStatus);
        // The rollout's counts just changed, so its version moves too (see raiseVersion).
        raiseVersion(campaign);

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
            publishProgress(campaign);
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
        publishProgress(campaign);

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
            publishProgress(campaign);
            return;
        }

        advanceIfStageFinished(campaign);
    }

    /**
     * Sends the rollout's current state to browsers and returns it. The version is raised first, so
     * every update carries a version no earlier update of this rollout had, even a second update in
     * the same transaction (see raiseVersion). Package-private for RolloutVersionTest.
     */
    CampaignResponse publishProgress(RolloutCampaign campaign) {
        raiseVersion(campaign);
        CampaignResponse response = mapToCampaignResponse(campaign);
        otaWebSocketService.notifyCampaignProgress(campaign.getId(), response);
        return response;
    }

    /**
     * A rollout's version stands for everything its response shows, but stage statuses and the
     * counts live in other tables, so changing them leaves the rollout row (and its version)
     * untouched. This raises it by one on every call, on the row this transaction already holds
     * FOR UPDATE, so writers still raise it in commit order.
     *
     * <p>Not {@code lock(PESSIMISTIC_FORCE_INCREMENT)}: Hibernate runs that once per transaction and
     * skips later calls, so a second update in the same transaction would repeat the version. Instead
     * pending changes are flushed, the version is raised with an UPDATE, and the entity is refreshed
     * so it holds the new version (and later flushes check against it).
     */
    private void raiseVersion(RolloutCampaign campaign) {
        campaignRepository.flush();
        campaignRepository.incrementVersion(campaign.getId());
        entityManager.refresh(campaign);
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
                
                publishProgress(campaign);
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
                failed,
                campaign.getVersion());
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
                status.getErrorMessage(),
                status.getVersion());
    }
}
