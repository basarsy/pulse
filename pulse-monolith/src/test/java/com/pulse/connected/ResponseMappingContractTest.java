package com.pulse.connected;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.connected.auth.Role;
import com.pulse.connected.auth.User;
import com.pulse.connected.auth.UserRepository;
import com.pulse.connected.command.CommandRateLimiter;
import com.pulse.connected.command.CommandService;
import com.pulse.connected.command.CommandTimeoutProperties;
import com.pulse.connected.command.CommandWebSocketService;
import com.pulse.connected.command.RemoteCommand;
import com.pulse.connected.command.RemoteCommandRepository;
import com.pulse.connected.common.AuditLogService;
import com.pulse.connected.domain.CampaignStatus;
import com.pulse.connected.domain.CommandStatus;
import com.pulse.connected.domain.CommandType;
import com.pulse.connected.domain.ConnectivityState;
import com.pulse.connected.domain.RolloutStageStatus;
import com.pulse.connected.domain.UpdateStatus;
import com.pulse.connected.ota.OtaWebSocketService;
import com.pulse.connected.ota.RolloutCampaign;
import com.pulse.connected.ota.RolloutCampaignRepository;
import com.pulse.connected.ota.RolloutCampaignService;
import com.pulse.connected.ota.RolloutStage;
import com.pulse.connected.ota.SoftwareVersion;
import com.pulse.connected.ota.SoftwareVersionRepository;
import com.pulse.connected.ota.SoftwareVersionService;
import com.pulse.connected.ota.StageStatusCount;
import com.pulse.connected.ota.VehicleUpdateStatus;
import com.pulse.connected.ota.VehicleUpdateStatusRepository;
import com.pulse.connected.telemetry.TelemetryService;
import com.pulse.connected.telemetry.TelemetrySnapshot;
import com.pulse.connected.telemetry.TelemetrySnapshotRepository;
import com.pulse.connected.vehicle.Vehicle;
import com.pulse.connected.vehicle.VehicleRepository;
import com.pulse.connected.vehicle.VehicleService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.ApplicationEventPublisher;
import jakarta.persistence.EntityManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins each service mapper field by field. Every source field gets a distinct value, so
 * passing two same-typed values in the wrong order changes the JSON and fails the test.
 */
@JsonTest
class ResponseMappingContractTest {

    private static final UUID ID_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ID_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ID_3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID ID_4 = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID ID_5 = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID ID_6 = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final UUID ID_7 = UUID.fromString("00000000-0000-0000-0000-000000000007");

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T5 = Instant.parse("2026-01-02T03:04:05Z");
    private static final Instant T6 = Instant.parse("2026-01-02T03:04:06Z");
    private static final Instant T7 = Instant.parse("2026-01-02T03:04:07Z");
    private static final Instant T8 = Instant.parse("2026-01-02T03:04:08Z");

    @Autowired
    private ObjectMapper json;

    private void assertJson(Object response, String expectedJson) throws Exception {
        JSONAssert.assertEquals(expectedJson, json.writeValueAsString(response), JSONCompareMode.STRICT);
    }

    private static User owner() {
        return User.builder().id(ID_3).email("owner@example.com").fullName("Owner").roles(Set.of(Role.OWNER)).createdAt(T0).build();
    }

    private static Vehicle vehicle() {
        User family = User.builder().id(ID_7).email("family@example.com").fullName("Family")
                .roles(Set.of(Role.FAMILY_MEMBER)).createdAt(T0).build();
        return Vehicle.builder()
                .id(ID_2)
                .vin("WVWZZZ1JZXW000001")
                .model("Model X")
                .modelYear(2024)
                .owner(owner())
                .authorizedUsers(new HashSet<>(Set.of(family)))
                .connectivityState(ConnectivityState.OFFLINE)
                .currentSoftwareVersion("v2.0.0")
                .lastSeenAt(T5)
                .batteryOrFuelLevel(81.5)
                .odometerKm(12345.6)
                .lastLat(52.1)
                .lastLng(4.3)
                .createdAt(T0)
                .build();
    }

    private static SoftwareVersion softwareVersion() {
        return SoftwareVersion.builder()
                .id(ID_4)
                .versionLabel("v2.1.0")
                .releaseNotes("Fixes")
                .checksum("sha256:abc")
                .packageUrl("https://example.com/v2.1.0.bin")
                .mandatory(true)
                .createdAt(T0)
                .build();
    }

    private static RolloutCampaign campaign() {
        RolloutCampaign campaign = RolloutCampaign.builder()
                .id(ID_1)
                .softwareVersion(softwareVersion())
                .targetModel("Model X")
                .status(CampaignStatus.PAUSED)
                .currentStageIndex(1)
                .failureThresholdPercent(12.5)
                .createdBy(owner())
                .createdAt(T5)
                .stages(new ArrayList<>())
                .build();
        campaign.getStages().add(RolloutStage.builder().id(ID_5).campaign(campaign).percentage(5)
                .status(RolloutStageStatus.COMPLETED).startedAt(T6).completedAt(T7).build());
        campaign.getStages().add(RolloutStage.builder().id(ID_6).campaign(campaign).percentage(100)
                .status(RolloutStageStatus.IN_PROGRESS).startedAt(T8).build());
        ReflectionTestUtils.setField(campaign, "version", 8L);
        return campaign;
    }

    private RolloutCampaignService rolloutService(VehicleUpdateStatusRepository updates) {
        return new RolloutCampaignService(
                mock(RolloutCampaignRepository.class),
                mock(SoftwareVersionRepository.class),
                mock(VehicleRepository.class),
                updates,
                mock(ApplicationEventPublisher.class),
                mock(OtaWebSocketService.class),
                new SoftwareVersionService(mock(SoftwareVersionRepository.class)),
                mock(AuditLogService.class),
                new SimpleMeterRegistry(),
                mock(EntityManager.class));
    }

    @Test
    void commandResponse_mapsEveryField() throws Exception {
        CommandService service = new CommandService(
                mock(RemoteCommandRepository.class),
                mock(VehicleRepository.class),
                mock(CommandRateLimiter.class),
                mock(ApplicationEventPublisher.class),
                mock(CommandWebSocketService.class),
                mock(AuditLogService.class),
                new SimpleMeterRegistry(),
                new CommandTimeoutProperties(Duration.ofMinutes(15), Duration.ofSeconds(60)),
                TransactionOperations.withoutTransaction());
        User issuer = User.builder().id(ID_7).email("issuer@example.com").fullName("Issuer").roles(Set.of(Role.OWNER)).build();
        RemoteCommand command = RemoteCommand.builder()
                .id(ID_1)
                .vehicle(vehicle())
                .issuedBy(issuer)
                .type(CommandType.REMOTE_START)
                .status(CommandStatus.ACKNOWLEDGED)
                .requestedAt(T5)
                .sentAt(T6)
                .resolvedAt(T7)
                .resultMessage("Engine started")
                .idempotencyKey("key-4")
                .build();
        ReflectionTestUtils.setField(command, "version", 7L);

        assertJson(service.mapToResponse(command), """
                {"id":"00000000-0000-0000-0000-000000000001","vehicleId":"00000000-0000-0000-0000-000000000002",
                 "issuedByUserId":"00000000-0000-0000-0000-000000000007","type":"REMOTE_START","status":"ACKNOWLEDGED",
                 "requestedAt":"2026-01-02T03:04:05Z","sentAt":"2026-01-02T03:04:06Z","resolvedAt":"2026-01-02T03:04:07Z",
                 "resultMessage":"Engine started","idempotencyKey":"key-4","version":7}
                """);
    }

    @Test
    void campaignResponse_mapsEveryField() throws Exception {
        VehicleUpdateStatusRepository updates = mock(VehicleUpdateStatusRepository.class);
        // total 9, installed 4, failed 2 (FAILED + ROLLED_BACK), across both stages.
        when(updates.countByStageAndStatus(ID_1)).thenReturn(List.of(
                new StageStatusCount(0, UpdateStatus.INSTALLED, 3),
                new StageStatusCount(0, UpdateStatus.FAILED, 1),
                new StageStatusCount(1, UpdateStatus.INSTALLED, 1),
                new StageStatusCount(1, UpdateStatus.ROLLED_BACK, 1),
                new StageStatusCount(1, UpdateStatus.DOWNLOADING, 3)));

        assertJson(rolloutService(updates).mapToCampaignResponse(campaign()), """
                {"id":"00000000-0000-0000-0000-000000000001",
                 "softwareVersion":{"id":"00000000-0000-0000-0000-000000000004","versionLabel":"v2.1.0",
                                    "releaseNotes":"Fixes","checksum":"sha256:abc","packageUrl":"https://example.com/v2.1.0.bin",
                                    "mandatory":true,"createdAt":"2026-01-01T00:00:00Z"},
                 "targetModel":"Model X","status":"PAUSED","currentStageIndex":1,"failureThresholdPercent":12.5,
                 "createdByUserId":"00000000-0000-0000-0000-000000000003","createdAt":"2026-01-02T03:04:05Z",
                 "stages":[{"id":"00000000-0000-0000-0000-000000000005","percentage":5,"status":"COMPLETED",
                            "startedAt":"2026-01-02T03:04:06Z","completedAt":"2026-01-02T03:04:07Z"},
                           {"id":"00000000-0000-0000-0000-000000000006","percentage":100,"status":"IN_PROGRESS",
                            "startedAt":"2026-01-02T03:04:08Z","completedAt":null}],
                 "totalVehiclesTargeted":9,"vehiclesInstalled":4,"vehiclesFailed":2,"version":8}
                """);
    }

    @Test
    void vehicleUpdateStatusResponse_mapsEveryField() throws Exception {
        VehicleUpdateStatus status = VehicleUpdateStatus.builder()
                .id(ID_6)
                .vehicle(vehicle())
                .campaign(campaign())
                .targetVersion(softwareVersion())
                .stageIndex(1)
                .status(UpdateStatus.ROLLED_BACK)
                .progressPercent(63)
                .startedAt(T5)
                .completedAt(T6)
                .errorMessage("boot loop")
                .build();
        ReflectionTestUtils.setField(status, "version", 9L);

        assertJson(rolloutService(mock(VehicleUpdateStatusRepository.class)).mapToUpdateStatusResponse(status), """
                {"id":"00000000-0000-0000-0000-000000000006","vehicleId":"00000000-0000-0000-0000-000000000002",
                 "vin":"WVWZZZ1JZXW000001","campaignId":"00000000-0000-0000-0000-000000000001",
                 "targetVersionId":"00000000-0000-0000-0000-000000000004","targetVersionLabel":"v2.1.0",
                 "status":"ROLLED_BACK","progressPercent":63,"startedAt":"2026-01-02T03:04:05Z",
                 "completedAt":"2026-01-02T03:04:06Z","errorMessage":"boot loop","version":9}
                """);
    }

    @Test
    void softwareVersionResponse_mapsEveryField() throws Exception {
        SoftwareVersionService service = new SoftwareVersionService(mock(SoftwareVersionRepository.class));

        assertJson(service.mapToResponse(softwareVersion()), """
                {"id":"00000000-0000-0000-0000-000000000004","versionLabel":"v2.1.0","releaseNotes":"Fixes",
                 "checksum":"sha256:abc","packageUrl":"https://example.com/v2.1.0.bin","mandatory":true,
                 "createdAt":"2026-01-01T00:00:00Z"}
                """);
    }

    @Test
    void telemetrySnapshotResponse_mapsEveryField() throws Exception {
        TelemetrySnapshotRepository snapshots = mock(TelemetrySnapshotRepository.class);
        VehicleRepository vehicles = mock(VehicleRepository.class);
        when(vehicles.existsById(ID_2)).thenReturn(true);
        when(snapshots.findTopByVehicleIdOrderByRecordedAtDesc(ID_2)).thenReturn(Optional.of(TelemetrySnapshot.builder()
                .id(ID_1)
                .vehicle(vehicle())
                .recordedAt(T5)
                .batteryOrFuelLevel(81.5)
                .odometerKm(12345.6)
                .engineTempC(90.25)
                .lat(52.1)
                .lng(4.3)
                .build()));
        TelemetryService service = new TelemetryService(snapshots, vehicles, new SimpleMeterRegistry());

        assertJson(service.getLatestSnapshot(ID_2), """
                {"id":"00000000-0000-0000-0000-000000000001","vehicleId":"00000000-0000-0000-0000-000000000002",
                 "recordedAt":"2026-01-02T03:04:05Z","batteryOrFuelLevel":81.5,"odometerKm":12345.6,
                 "engineTempC":90.25,"lat":52.1,"lng":4.3}
                """);
    }

    @Test
    void vehicleResponse_mapsEveryField() throws Exception {
        VehicleService service = new VehicleService(mock(VehicleRepository.class),
                mock(UserRepository.class), mock(AuditLogService.class));

        assertJson(service.mapToResponse(vehicle()), """
                {"id":"00000000-0000-0000-0000-000000000002","vin":"WVWZZZ1JZXW000001","model":"Model X",
                 "modelYear":2024,"ownerId":"00000000-0000-0000-0000-000000000003","ownerEmail":"owner@example.com",
                 "authorizedUserIds":["00000000-0000-0000-0000-000000000007"],"connectivityState":"OFFLINE",
                 "currentSoftwareVersion":"v2.0.0","lastSeenAt":"2026-01-02T03:04:05Z","batteryOrFuelLevel":81.5,
                 "odometerKm":12345.6,"lastLat":52.1,"lastLng":4.3,"createdAt":"2026-01-01T00:00:00Z"}
                """);
    }
}
