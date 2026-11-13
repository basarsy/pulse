package com.pulse.connected.ota;

import com.pulse.connected.auth.Role;
import com.pulse.connected.auth.User;
import com.pulse.connected.messaging.dto.OtaInstructionEvent;
import com.pulse.connected.messaging.dto.OtaUpdateProgressEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.pulse.connected.ota.dto.*;
import com.pulse.connected.domain.CampaignStatus;
import com.pulse.connected.domain.ConnectivityState;
import com.pulse.connected.domain.RolloutStageStatus;
import com.pulse.connected.domain.UpdateStatus;
import com.pulse.connected.vehicle.Vehicle;
import com.pulse.connected.vehicle.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RolloutCampaignServiceTest {

    @Mock
    private RolloutCampaignRepository campaignRepository;

    @Mock
    private SoftwareVersionRepository softwareVersionRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private VehicleUpdateStatusRepository vehicleUpdateStatusRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private OtaWebSocketService otaWebSocketService;

    @Mock
    private SoftwareVersionService softwareVersionService;

    @Mock
    private com.pulse.connected.common.AuditLogService auditLogService;

    @org.mockito.Spy
    private io.micrometer.core.instrument.MeterRegistry meterRegistry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();

    @InjectMocks
    private RolloutCampaignService campaignService;

    private User admin;
    private SoftwareVersion version;
    private Vehicle vehicle1;
    private Vehicle vehicle2;
    private RolloutCampaign campaign;

    @BeforeEach
    void setUp() {
        admin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@example.com")
                .fullName("Admin User")
                .roles(Set.of(Role.FLEET_ADMIN))
                .build();

        version = SoftwareVersion.builder()
                .id(UUID.randomUUID())
                .versionLabel("2026.07.1")
                .packageUrl("https://ota.pulse.com/packages/v2026.07.1.bin")
                .checksum("sha256checksum")
                .mandatory(false)
                .build();

        vehicle1 = Vehicle.builder()
                .id(UUID.randomUUID())
                .vin("WBA11111111111111")
                .model("BMW iX xDrive50")
                .modelYear(2026)
                .connectivityState(ConnectivityState.ONLINE)
                .currentSoftwareVersion("2026.06.1")
                .build();

        vehicle2 = Vehicle.builder()
                .id(UUID.randomUUID())
                .vin("WBA22222222222222")
                .model("BMW iX xDrive50")
                .modelYear(2026)
                .connectivityState(ConnectivityState.ONLINE)
                .currentSoftwareVersion("2026.06.1")
                .build();

        RolloutStage stage1 = RolloutStage.builder()
                .id(UUID.randomUUID())
                .percentage(50)
                .status(RolloutStageStatus.PENDING)
                .build();

        RolloutStage stage2 = RolloutStage.builder()
                .id(UUID.randomUUID())
                .percentage(100)
                .status(RolloutStageStatus.PENDING)
                .build();

        campaign = RolloutCampaign.builder()
                .id(UUID.randomUUID())
                .softwareVersion(version)
                .targetModel("BMW iX xDrive50")
                .status(CampaignStatus.DRAFT)
                .currentStageIndex(0)
                .failureThresholdPercent(10.0)
                .createdBy(admin)
                .stages(new ArrayList<>(List.of(stage1, stage2)))
                .build();

        stage1.setCampaign(campaign);
        stage2.setCampaign(campaign);
    }

    @Test
    void createCampaign_Success() {
        CreateCampaignRequest request = CreateCampaignRequest.builder()
                .softwareVersionId(version.getId())
                .targetModel("BMW iX xDrive50")
                .failureThresholdPercent(15.0)
                .stagePercentages(List.of(10, 50, 100))
                .build();

        when(softwareVersionRepository.findById(version.getId())).thenReturn(Optional.of(version));
        when(campaignRepository.save(any(RolloutCampaign.class))).thenReturn(campaign);
        when(softwareVersionService.mapToResponse(version)).thenReturn(new SoftwareVersionResponse());

        CampaignResponse response = campaignService.createCampaign(request, admin);

        assertThat(response).isNotNull();
        verify(campaignRepository).save(any(RolloutCampaign.class));
    }

    @Test
    void startCampaign_DispatchesFirstStageToItsShareOfTheFleet() {
        when(campaignRepository.findById(campaign.getId())).thenReturn(Optional.of(campaign));
        when(campaignRepository.save(any(RolloutCampaign.class))).thenReturn(campaign);
        when(vehicleRepository.findAllByModelIgnoreCaseOrderByVinAsc(campaign.getTargetModel()))
                .thenReturn(List.of(vehicle1, vehicle2));
        when(vehicleUpdateStatusRepository.findAllByCampaignId(campaign.getId())).thenReturn(List.of());
        when(vehicleUpdateStatusRepository.save(any())).thenAnswer(saved());

        CampaignResponse response = campaignService.startCampaign(campaign.getId(), admin);

        assertThat(response).isNotNull();
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.IN_PROGRESS);
        // Stage 1 is 50% of two vehicles: exactly one instruction, to the first vehicle by VIN.
        ArgumentCaptor<OtaInstructionEvent> sent = ArgumentCaptor.forClass(OtaInstructionEvent.class);
        verify(eventPublisher).publishEvent(sent.capture());
        assertThat(sent.getValue().getVehicleId()).isEqualTo(vehicle1.getId());
    }

    @Test
    void laterStage_OnlyInstructsVehiclesNotTargetedInEarlierStages() {
        campaign.setStatus(CampaignStatus.IN_PROGRESS);
        campaign.setCurrentStageIndex(1); // 100%
        VehicleUpdateStatus stage1Update = update(vehicle1, 0, UpdateStatus.INSTALLED);
        when(vehicleRepository.findAllByModelIgnoreCaseOrderByVinAsc(campaign.getTargetModel()))
                .thenReturn(List.of(vehicle1, vehicle2));
        when(vehicleUpdateStatusRepository.findAllByCampaignId(campaign.getId())).thenReturn(List.of(stage1Update));
        when(vehicleUpdateStatusRepository.save(any())).thenAnswer(saved());

        campaignService.executeActiveStage(campaign);

        ArgumentCaptor<OtaInstructionEvent> sent = ArgumentCaptor.forClass(OtaInstructionEvent.class);
        verify(eventPublisher).publishEvent(sent.capture());
        assertThat(sent.getValue().getVehicleId()).isEqualTo(vehicle2.getId());
        assertThat(stage1Update.getStatus()).isEqualTo(UpdateStatus.INSTALLED);
    }

    @Test
    void vehiclesAlreadyOnTargetVersion_AreNotInstructed() {
        campaign.setStatus(CampaignStatus.IN_PROGRESS);
        campaign.setCurrentStageIndex(1); // 100%
        vehicle1.setCurrentSoftwareVersion(version.getVersionLabel());
        when(vehicleRepository.findAllByModelIgnoreCaseOrderByVinAsc(campaign.getTargetModel()))
                .thenReturn(List.of(vehicle1, vehicle2));
        when(vehicleUpdateStatusRepository.findAllByCampaignId(campaign.getId())).thenReturn(List.of());
        when(vehicleUpdateStatusRepository.save(any())).thenAnswer(saved());

        campaignService.executeActiveStage(campaign);

        ArgumentCaptor<OtaInstructionEvent> sent = ArgumentCaptor.forClass(OtaInstructionEvent.class);
        verify(eventPublisher).publishEvent(sent.capture());
        assertThat(sent.getValue().getVehicleId()).isEqualTo(vehicle2.getId());
    }

    @Test
    void failureRate_IsEvaluatedPerStage() {
        // Stage 1: two successes. Stage 2: one failure out of two (50%). Campaign-wide that is
        // 25%, below the 30% threshold, but the current stage is clearly unhealthy.
        campaign.setStatus(CampaignStatus.IN_PROGRESS);
        campaign.setCurrentStageIndex(1);
        campaign.setFailureThresholdPercent(30.0);
        Vehicle vehicle3 = Vehicle.builder().id(UUID.randomUUID()).vin("WBA33333333333333").model(campaign.getTargetModel()).build();
        Vehicle vehicle4 = Vehicle.builder().id(UUID.randomUUID()).vin("WBA44444444444444").model(campaign.getTargetModel()).build();
        VehicleUpdateStatus failing = update(vehicle4, 1, UpdateStatus.INSTALLING);
        List<VehicleUpdateStatus> all = List.of(
                update(vehicle1, 0, UpdateStatus.INSTALLED),
                update(vehicle2, 0, UpdateStatus.INSTALLED),
                update(vehicle3, 1, UpdateStatus.DOWNLOADING),
                failing);
        when(vehicleUpdateStatusRepository.findById(failing.getId())).thenReturn(Optional.of(failing));
        when(vehicleUpdateStatusRepository.save(any())).thenAnswer(saved());
        when(vehicleUpdateStatusRepository.findAllByCampaignId(campaign.getId())).thenReturn(all);
        when(softwareVersionService.mapToResponse(version)).thenReturn(new SoftwareVersionResponse());

        campaignService.handleVehicleUpdateProgress(progress(failing, UpdateStatus.FAILED));

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.PAUSED);
    }

    @Test
    void staleProgressEvent_DoesNotMoveStatusBackwards() {
        VehicleUpdateStatus installed = update(vehicle1, 0, UpdateStatus.INSTALLED);
        when(vehicleUpdateStatusRepository.findById(installed.getId())).thenReturn(Optional.of(installed));

        campaignService.handleVehicleUpdateProgress(progress(installed, UpdateStatus.DOWNLOADING));

        assertThat(installed.getStatus()).isEqualTo(UpdateStatus.INSTALLED);
        verify(vehicleUpdateStatusRepository, never()).save(any());
    }

    @Test
    void failedUpdate_CanStillBeReportedAsRolledBack() {
        VehicleUpdateStatus failed = update(vehicle1, 0, UpdateStatus.FAILED);
        campaign.setStatus(CampaignStatus.PAUSED);
        when(vehicleUpdateStatusRepository.findById(failed.getId())).thenReturn(Optional.of(failed));
        when(vehicleUpdateStatusRepository.save(any())).thenAnswer(saved());

        campaignService.handleVehicleUpdateProgress(progress(failed, UpdateStatus.ROLLED_BACK));

        assertThat(failed.getStatus()).isEqualTo(UpdateStatus.ROLLED_BACK);
    }

    private VehicleUpdateStatus update(Vehicle vehicle, int stageIndex, UpdateStatus status) {
        return VehicleUpdateStatus.builder()
                .id(UUID.randomUUID())
                .vehicle(vehicle)
                .campaign(campaign)
                .targetVersion(version)
                .stageIndex(stageIndex)
                .status(status)
                .build();
    }

    private OtaUpdateProgressEvent progress(VehicleUpdateStatus update, UpdateStatus status) {
        return OtaUpdateProgressEvent.builder()
                .updateStatusId(update.getId())
                .vehicleId(update.getVehicle().getId())
                .campaignId(campaign.getId())
                .status(status)
                .progressPercent(50)
                .build();
    }

    private static Answer<VehicleUpdateStatus> saved() {
        return invocation -> {
            VehicleUpdateStatus status = invocation.getArgument(0);
            if (status.getId() == null) {
                status.setId(UUID.randomUUID());
            }
            return status;
        };
    }

    @Test
    void handleVehicleUpdateProgress_AutoPausesWhenFailureThresholdExceeded() {
        VehicleUpdateStatus updateStatus = VehicleUpdateStatus.builder()
                .id(UUID.randomUUID())
                .vehicle(vehicle1)
                .campaign(campaign)
                .targetVersion(version)
                .status(UpdateStatus.PENDING)
                .build();

        campaign.setStatus(CampaignStatus.IN_PROGRESS);

        when(vehicleUpdateStatusRepository.findById(updateStatus.getId())).thenReturn(Optional.of(updateStatus));
        when(vehicleUpdateStatusRepository.save(any())).thenReturn(updateStatus);
        when(vehicleUpdateStatusRepository.findAllByCampaignId(campaign.getId())).thenReturn(List.of(updateStatus));
        when(softwareVersionService.mapToResponse(version)).thenReturn(new SoftwareVersionResponse());

        OtaUpdateProgressEvent failedEvent = OtaUpdateProgressEvent.builder()
                .updateStatusId(updateStatus.getId())
                .vehicleId(vehicle1.getId())
                .campaignId(campaign.getId())
                .status(UpdateStatus.FAILED)
                .errorMessage("ECU checksum mismatch")
                .build();

        campaignService.handleVehicleUpdateProgress(failedEvent);

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.PAUSED);
        verify(campaignRepository).save(campaign);
        verify(otaWebSocketService).notifyCampaignProgress(eq(campaign.getId()), any());
    }

    @Test
    void pauseCampaign_AuditsActingUserRatherThanCreator() {
        User otherAdmin = User.builder()
                .id(UUID.randomUUID())
                .email("other-admin@example.com")
                .fullName("Other Admin")
                .roles(Set.of(Role.SYSTEM_ADMIN))
                .build();
        campaign.setStatus(CampaignStatus.IN_PROGRESS);
        when(campaignRepository.findById(campaign.getId())).thenReturn(Optional.of(campaign));
        when(campaignRepository.save(campaign)).thenReturn(campaign);

        campaignService.pauseCampaign(campaign.getId(), otherAdmin);

        verify(auditLogService).log(eq(otherAdmin.getId()), eq("CAMPAIGN_PAUSED"), any(), any(), any());
    }

    @Test
    void abortCampaign_AlreadyCompleted_IsRejected() {
        campaign.setStatus(CampaignStatus.COMPLETED);
        when(campaignRepository.findById(campaign.getId())).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> campaignService.abortCampaign(campaign.getId(), admin))
                .isInstanceOf(IllegalStateException.class);
        verify(campaignRepository, never()).save(any());
    }
}
