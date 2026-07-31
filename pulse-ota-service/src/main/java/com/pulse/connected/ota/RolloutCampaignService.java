package com.pulse.connected.ota;

import com.pulse.connected.client.VehicleRegistryClient;
import com.pulse.connected.client.VehicleRegistryClient.VehicleInfo;
import com.pulse.connected.domain.CampaignStatus;
import com.pulse.connected.domain.RolloutStageStatus;
import com.pulse.connected.domain.UpdateStatus;
import com.pulse.connected.common.exception.ResourceNotFoundException;
import com.pulse.connected.messaging.OtaKafkaProducer;
import com.pulse.connected.messaging.dto.OtaInstructionEvent;
import com.pulse.connected.messaging.dto.OtaUpdateProgressEvent;
import com.pulse.connected.ota.dto.*;
import com.pulse.connected.common.AuditLogService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RolloutCampaignService {

    private static final Logger log = LoggerFactory.getLogger(RolloutCampaignService.class);

    private final RolloutCampaignRepository campaignRepository;
    private final SoftwareVersionRepository softwareVersionRepository;
    private final VehicleUpdateStatusRepository vehicleUpdateStatusRepository;
    private final SoftwareVersionService softwareVersionService;
    private final VehicleRegistryClient vehicleRegistryClient;
    private final OtaKafkaProducer otaKafkaProducer;
    private final AuditLogService auditLogService;
    private final MeterRegistry meterRegistry;

    public RolloutCampaignService(
            RolloutCampaignRepository campaignRepository,
            SoftwareVersionRepository softwareVersionRepository,
            VehicleUpdateStatusRepository vehicleUpdateStatusRepository,
            SoftwareVersionService softwareVersionService,
            VehicleRegistryClient vehicleRegistryClient,
            OtaKafkaProducer otaKafkaProducer,
            AuditLogService auditLogService,
            MeterRegistry meterRegistry) {
        this.campaignRepository = campaignRepository;
        this.softwareVersionRepository = softwareVersionRepository;
        this.vehicleUpdateStatusRepository = vehicleUpdateStatusRepository;
        this.softwareVersionService = softwareVersionService;
        this.vehicleRegistryClient = vehicleRegistryClient;
        this.otaKafkaProducer = otaKafkaProducer;
        this.auditLogService = auditLogService;
        this.meterRegistry = meterRegistry;

        this.meterRegistry.gauge("pulse.rollout.campaigns.active", this.campaignRepository,
                repo -> repo.findAll().stream().filter(c -> c.getStatus() == CampaignStatus.IN_PROGRESS).count());
    }

    @Transactional
    public CampaignResponse createCampaign(CreateCampaignRequest request, UUID currentUser) {
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
                .createdById(currentUser)
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
        auditLogService.log(currentUser, "CAMPAIGN_CREATED", "RolloutCampaign", saved.getId().toString(), null);
        return mapToCampaignResponse(saved);
    }

    @Transactional
    public CampaignResponse startCampaign(UUID campaignId) {
        RolloutCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Rollout campaign not found with ID: " + campaignId));

        if (campaign.getStatus() != CampaignStatus.DRAFT && campaign.getStatus() != CampaignStatus.PAUSED) {
            throw new IllegalStateException("Cannot start campaign in status: " + campaign.getStatus());
        }

        campaign.setStatus(CampaignStatus.IN_PROGRESS);
        RolloutCampaign saved = campaignRepository.save(campaign);

        UUID actorId = campaign.getCreatedById();
        auditLogService.log(actorId, "CAMPAIGN_STARTED", "RolloutCampaign", campaignId.toString(), null);

        executeActiveStage(saved);
        return mapToCampaignResponse(saved);
    }

    @Transactional
    public CampaignResponse pauseCampaign(UUID campaignId) {
        RolloutCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Rollout campaign not found with ID: " + campaignId));

        if (campaign.getStatus() != CampaignStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot pause campaign in status: " + campaign.getStatus());
        }

        campaign.setStatus(CampaignStatus.PAUSED);
        RolloutCampaign saved = campaignRepository.save(campaign);

        UUID actorId = campaign.getCreatedById();
        auditLogService.log(actorId, "CAMPAIGN_PAUSED", "RolloutCampaign", campaignId.toString(), null);

        return mapToCampaignResponse(saved);
    }

    @Transactional
    public CampaignResponse resumeCampaign(UUID campaignId) {
        return startCampaign(campaignId);
    }

    @Transactional
    public CampaignResponse abortCampaign(UUID campaignId) {
        RolloutCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Rollout campaign not found with ID: " + campaignId));

        campaign.setStatus(CampaignStatus.ABORTED);
        RolloutCampaign saved = campaignRepository.save(campaign);

        UUID actorId = campaign.getCreatedById();
        auditLogService.log(actorId, "CAMPAIGN_ABORTED", "RolloutCampaign", campaignId.toString(), null);

        return mapToCampaignResponse(saved);
    }

    @Transactional
    public void handleVehicleUpdateProgress(OtaUpdateProgressEvent event) {
        VehicleUpdateStatus updateStatus = vehicleUpdateStatusRepository.findById(event.getUpdateStatusId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle update status not found with ID: " + event.getUpdateStatusId()));

        updateStatus.setStatus(event.getStatus());
        updateStatus.setProgressPercent(event.getProgressPercent() != null ? event.getProgressPercent() : 0);

        if (event.getStatus() == UpdateStatus.DOWNLOADING && updateStatus.getStartedAt() == null) {
            updateStatus.setStartedAt(Instant.now());
        }

        if (event.getStatus() == UpdateStatus.INSTALLED) {
            updateStatus.setCompletedAt(Instant.now());
            // Update vehicle software version in vehicle registry service via internal REST client
            vehicleRegistryClient.updateVehicleSoftwareVersion(updateStatus.getVehicleId(), updateStatus.getTargetVersion().getVersionLabel());
        } else if (event.getStatus() == UpdateStatus.FAILED || event.getStatus() == UpdateStatus.ROLLED_BACK) {
            updateStatus.setCompletedAt(Instant.now());
            updateStatus.setErrorMessage(event.getErrorMessage() != null ? event.getErrorMessage() : "Installation failed");
        }

        VehicleUpdateStatus savedStatus = vehicleUpdateStatusRepository.save(updateStatus);
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
            return;
        }

        RolloutStage activeStage = stages.get(stageIdx);
        activeStage.setStatus(RolloutStageStatus.IN_PROGRESS);
        if (activeStage.getStartedAt() == null) {
            activeStage.setStartedAt(Instant.now());
        }

        // Target vehicles via vehicle-registry internal client
        List<VehicleInfo> matchingVehicles = vehicleRegistryClient.getVehiclesByModel(campaign.getTargetModel());
        int totalMatching = matchingVehicles.size();
        int targetCount = (int) Math.ceil((activeStage.getPercentage() / 100.0) * totalMatching);
        if (targetCount == 0 && totalMatching > 0) {
            targetCount = 1;
        }

        List<VehicleInfo> stageVehicles = matchingVehicles.stream().limit(targetCount).collect(Collectors.toList());

        log.info("Executing campaign [{}] stage {} ({}%). Targeting {} out of {} vehicles.",
                campaign.getId(), stageIdx + 1, activeStage.getPercentage(), stageVehicles.size(), totalMatching);

        for (VehicleInfo vehicle : stageVehicles) {
            boolean alreadyTargeted = vehicleUpdateStatusRepository.findByVehicleIdAndCampaignId(vehicle.id(), campaign.getId()).isPresent();
            if (!alreadyTargeted) {
                VehicleUpdateStatus updateStatus = VehicleUpdateStatus.builder()
                        .vehicleId(vehicle.id())
                        .campaign(campaign)
                        .targetVersion(campaign.getSoftwareVersion())
                        .status(UpdateStatus.PENDING)
                        .progressPercent(0)
                        .build();

                VehicleUpdateStatus savedUpdate = vehicleUpdateStatusRepository.save(updateStatus);

                OtaInstructionEvent instruction = OtaInstructionEvent.builder()
                        .updateStatusId(savedUpdate.getId())
                        .vehicleId(vehicle.id())
                        .campaignId(campaign.getId())
                        .targetVersionId(campaign.getSoftwareVersion().getId())
                        .targetVersionLabel(campaign.getSoftwareVersion().getVersionLabel())
                        .packageUrl(campaign.getSoftwareVersion().getPackageUrl())
                        .checksum(campaign.getSoftwareVersion().getChecksum())
                        .build();

                otaKafkaProducer.sendOtaInstruction(instruction);
            }
        }

        campaignRepository.save(campaign);
    }

    private void evaluateCampaignStage(RolloutCampaign campaign) {
        List<VehicleUpdateStatus> updates = vehicleUpdateStatusRepository.findAllByCampaignId(campaign.getId());
        if (updates.isEmpty()) return;

        long totalTargeted = updates.size();
        long failedCount = updates.stream()
                .filter(u -> u.getStatus() == UpdateStatus.FAILED || u.getStatus() == UpdateStatus.ROLLED_BACK)
                .count();

        double failureRate = ((double) failedCount / totalTargeted) * 100.0;

        log.info("Campaign [{}] stage evaluation: {}/{} failed ({}% vs threshold {}%)",
                campaign.getId(), failedCount, totalTargeted, String.format("%.2f", failureRate), campaign.getFailureThresholdPercent());

        if (failureRate > campaign.getFailureThresholdPercent() && campaign.getStatus() == CampaignStatus.IN_PROGRESS) {
            log.warn("AUTO-PAUSING campaign [{}]! Failure rate ({}%) exceeded threshold ({}%)",
                    campaign.getId(), String.format("%.2f", failureRate), campaign.getFailureThresholdPercent());

            campaign.setStatus(CampaignStatus.PAUSED);
            campaignRepository.save(campaign);
            return;
        }

        long completedCount = updates.stream()
                .filter(u -> u.getStatus() == UpdateStatus.INSTALLED)
                .count();

        if (completedCount + failedCount >= totalTargeted && campaign.getStatus() == CampaignStatus.IN_PROGRESS) {
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

                UUID actorId = campaign.getCreatedById();
                auditLogService.log(actorId, "CAMPAIGN_COMPLETED", "RolloutCampaign", campaign.getId().toString(), null);
            }
        }
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
    public List<VehicleUpdateStatusResponse> getVehicleIdUpdateHistory(UUID vehicleId) {
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
                .createdByUserId(campaign.getCreatedById())
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
                .vehicleId(status.getVehicleId())
                .vin("VIN_STUB")
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
