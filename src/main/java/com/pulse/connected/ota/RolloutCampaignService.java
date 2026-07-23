package com.pulse.connected.ota;

import com.pulse.connected.auth.User;
import com.pulse.connected.common.exception.ResourceNotFoundException;
import com.pulse.connected.messaging.OtaKafkaProducer;
import com.pulse.connected.messaging.dto.OtaInstructionEvent;
import com.pulse.connected.messaging.dto.OtaUpdateProgressEvent;
import com.pulse.connected.ota.dto.*;
import com.pulse.connected.vehicle.Vehicle;
import com.pulse.connected.vehicle.VehicleRepository;
import com.pulse.connected.websocket.OtaWebSocketService;
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
    private final VehicleRepository vehicleRepository;
    private final VehicleUpdateStatusRepository vehicleUpdateStatusRepository;
    private final OtaKafkaProducer otaKafkaProducer;
    private final OtaWebSocketService otaWebSocketService;
    private final SoftwareVersionService softwareVersionService;

    public RolloutCampaignService(
            RolloutCampaignRepository campaignRepository,
            SoftwareVersionRepository softwareVersionRepository,
            VehicleRepository vehicleRepository,
            VehicleUpdateStatusRepository vehicleUpdateStatusRepository,
            OtaKafkaProducer otaKafkaProducer,
            OtaWebSocketService otaWebSocketService,
            SoftwareVersionService softwareVersionService) {
        this.campaignRepository = campaignRepository;
        this.softwareVersionRepository = softwareVersionRepository;
        this.vehicleRepository = vehicleRepository;
        this.vehicleUpdateStatusRepository = vehicleUpdateStatusRepository;
        this.otaKafkaProducer = otaKafkaProducer;
        this.otaWebSocketService = otaWebSocketService;
        this.softwareVersionService = softwareVersionService;
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

        CampaignResponse response = mapToCampaignResponse(saved);
        otaWebSocketService.notifyCampaignProgress(campaignId, response);
        return response;
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

        CampaignResponse response = mapToCampaignResponse(saved);
        otaWebSocketService.notifyCampaignProgress(campaignId, response);
        return response;
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
            // Update physical vehicle software version
            Vehicle vehicle = updateStatus.getVehicle();
            vehicle.setCurrentSoftwareVersion(updateStatus.getTargetVersion().getVersionLabel());
            vehicleRepository.save(vehicle);
        } else if (event.getStatus() == UpdateStatus.FAILED || event.getStatus() == UpdateStatus.ROLLED_BACK) {
            updateStatus.setCompletedAt(Instant.now());
            updateStatus.setErrorMessage(event.getErrorMessage() != null ? event.getErrorMessage() : "Installation failed");
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

        // Find all vehicles matching model
        List<Vehicle> eligibleVehicles = vehicleRepository.findAll().stream()
                .filter(v -> v.getModel().equalsIgnoreCase(campaign.getTargetModel()))
                .collect(Collectors.toList());

        if (eligibleVehicles.isEmpty()) {
            log.warn("No vehicles found for target model [{}] in campaign [{}]", campaign.getTargetModel(), campaign.getId());
            return;
        }

        int targetCount = (int) Math.ceil((eligibleVehicles.size() * activeStage.getPercentage()) / 100.0);
        List<Vehicle> stageVehicles = eligibleVehicles.subList(0, Math.min(targetCount, eligibleVehicles.size()));

        log.info("Executing campaign [{}] stage {} ({}%). Targeting {} out of {} vehicles.",
                campaign.getId(), stageIdx + 1, activeStage.getPercentage(), stageVehicles.size(), eligibleVehicles.size());

        for (Vehicle vehicle : stageVehicles) {
            VehicleUpdateStatus status = vehicleUpdateStatusRepository.findByVehicleIdAndCampaignId(vehicle.getId(), campaign.getId())
                    .orElseGet(() -> VehicleUpdateStatus.builder()
                            .vehicle(vehicle)
                            .campaign(campaign)
                            .targetVersion(campaign.getSoftwareVersion())
                            .status(UpdateStatus.PENDING)
                            .progressPercent(0)
                            .build());

            VehicleUpdateStatus savedStatus = vehicleUpdateStatusRepository.save(status);

            OtaInstructionEvent instruction = OtaInstructionEvent.builder()
                    .updateStatusId(savedStatus.getId())
                    .vehicleId(vehicle.getId())
                    .campaignId(campaign.getId())
                    .targetVersionId(campaign.getSoftwareVersion().getId())
                    .targetVersionLabel(campaign.getSoftwareVersion().getVersionLabel())
                    .packageUrl(campaign.getSoftwareVersion().getPackageUrl())
                    .checksum(campaign.getSoftwareVersion().getChecksum())
                    .build();

            otaKafkaProducer.dispatchOtaInstruction(instruction);
        }

        campaignRepository.save(campaign);
        otaWebSocketService.notifyCampaignProgress(campaign.getId(), mapToCampaignResponse(campaign));
    }

    private void evaluateCampaignStage(RolloutCampaign campaign) {
        List<VehicleUpdateStatus> updates = vehicleUpdateStatusRepository.findAllByCampaignId(campaign.getId());
        if (updates.isEmpty()) return;

        long totalTargeted = updates.size();
        long failedCount = updates.stream()
                .filter(u -> u.getStatus() == UpdateStatus.FAILED || u.getStatus() == UpdateStatus.ROLLED_BACK)
                .count();

        double failureRate = ((double) failedCount / totalTargeted) * 100.0;

        log.info("Campaign [{}] stage evaluation: {}/{} failed ({%.2f}%% vs threshold {%.2f}%%)",
                campaign.getId(), failedCount, totalTargeted, failureRate, campaign.getFailureThresholdPercent());

        // Check auto-pause threshold
        if (failureRate > campaign.getFailureThresholdPercent() && campaign.getStatus() == CampaignStatus.IN_PROGRESS) {
            log.warn("AUTO-PAUSING campaign [{}]! Failure rate ({%.2f}%%) exceeded threshold ({%.2f}%%)",
                    campaign.getId(), failureRate, campaign.getFailureThresholdPercent());

            campaign.setStatus(CampaignStatus.PAUSED);
            campaignRepository.save(campaign);
            otaWebSocketService.notifyCampaignProgress(campaign.getId(), mapToCampaignResponse(campaign));
            return;
        }

        long completedCount = updates.stream()
                .filter(u -> u.getStatus() == UpdateStatus.INSTALLED)
                .count();

        // If all active targeted vehicles finished (either installed or failed)
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
                otaWebSocketService.notifyCampaignProgress(campaign.getId(), mapToCampaignResponse(campaign));
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
