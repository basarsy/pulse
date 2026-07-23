package com.pulse.connected.ota;

import com.pulse.connected.auth.Role;
import com.pulse.connected.auth.User;
import com.pulse.connected.messaging.OtaKafkaProducer;
import com.pulse.connected.messaging.dto.OtaUpdateProgressEvent;
import com.pulse.connected.ota.dto.*;
import com.pulse.connected.vehicle.ConnectivityState;
import com.pulse.connected.vehicle.Vehicle;
import com.pulse.connected.vehicle.VehicleRepository;
import com.pulse.connected.websocket.OtaWebSocketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
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
    private OtaKafkaProducer otaKafkaProducer;

    @Mock
    private OtaWebSocketService otaWebSocketService;

    @Mock
    private SoftwareVersionService softwareVersionService;

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
    void startCampaign_ExecutesStageAndDispatchesKafka() {
        when(campaignRepository.findById(campaign.getId())).thenReturn(Optional.of(campaign));
        when(campaignRepository.save(any(RolloutCampaign.class))).thenReturn(campaign);
        when(vehicleRepository.findAll()).thenReturn(List.of(vehicle1, vehicle2));

        VehicleUpdateStatus updateStatus = VehicleUpdateStatus.builder()
                .id(UUID.randomUUID())
                .vehicle(vehicle1)
                .campaign(campaign)
                .targetVersion(version)
                .status(UpdateStatus.PENDING)
                .build();

        when(vehicleUpdateStatusRepository.findByVehicleIdAndCampaignId(any(), any())).thenReturn(Optional.of(updateStatus));
        when(vehicleUpdateStatusRepository.save(any())).thenReturn(updateStatus);

        CampaignResponse response = campaignService.startCampaign(campaign.getId());

        assertThat(response).isNotNull();
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.IN_PROGRESS);
        verify(otaKafkaProducer, atLeastOnce()).dispatchOtaInstruction(any());
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
}
