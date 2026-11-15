package com.pulse.connected;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.connected.auth.dto.AuthResponse;
import com.pulse.connected.auth.dto.LoginRequest;
import com.pulse.connected.auth.dto.RefreshTokenRequest;
import com.pulse.connected.auth.dto.RegisterRequest;
import com.pulse.connected.auth.dto.UserResponse;
import com.pulse.connected.command.dto.CommandResponse;
import com.pulse.connected.command.dto.IssueCommandRequest;
import com.pulse.connected.common.dto.ErrorResponse;
import com.pulse.connected.messaging.dto.CommandResultEvent;
import com.pulse.connected.messaging.dto.OtaInstructionEvent;
import com.pulse.connected.messaging.dto.OtaUpdateProgressEvent;
import com.pulse.connected.messaging.dto.VehicleCommandEvent;
import com.pulse.connected.messaging.dto.VehicleHeartbeatEvent;
import com.pulse.connected.messaging.dto.VehicleTelemetryEvent;
import com.pulse.connected.ota.dto.CampaignResponse;
import com.pulse.connected.ota.dto.CreateCampaignRequest;
import com.pulse.connected.ota.dto.CreateSoftwareVersionRequest;
import com.pulse.connected.ota.dto.RolloutStageResponse;
import com.pulse.connected.ota.dto.SoftwareVersionResponse;
import com.pulse.connected.ota.dto.VehicleUpdateStatusResponse;
import com.pulse.connected.telemetry.dto.IngestTelemetryRequest;
import com.pulse.connected.telemetry.dto.TelemetrySnapshotResponse;
import com.pulse.connected.vehicle.dto.AuthorizeUserRequest;
import com.pulse.connected.vehicle.dto.CreateVehicleRequest;
import com.pulse.connected.vehicle.dto.VehicleResponse;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

/**
 * Pins the JSON of every DTO (REST bodies, Kafka events, WebSocket payloads).
 * Each fixture is read into the DTO with the app's ObjectMapper and written back;
 * the output must equal the fixture exactly, so a renamed, missing or extra field fails.
 */
@JsonTest
class DtoJsonContractTest {

    @Autowired
    private ObjectMapper json;

    private void assertRoundTrip(Class<?> type, String expectedJson) throws Exception {
        assertReadsAs(type, expectedJson, expectedJson);
    }

    private void assertReadsAs(Class<?> type, String input, String expectedJson) throws Exception {
        Object value = json.readValue(input, type);
        JSONAssert.assertEquals(expectedJson, json.writeValueAsString(value), JSONCompareMode.STRICT);
    }

    // --- auth ---

    @Test
    void authResponse_roundTrips() throws Exception {
        assertRoundTrip(AuthResponse.class, """
                {"accessToken":"access-1","refreshToken":"refresh-2","tokenType":"Bearer","expiresInMs":900000,
                 "user":{"id":"00000000-0000-0000-0000-000000000001","email":"a@example.com","fullName":"Ada Lovelace",
                         "roles":["FLEET_ADMIN"],"createdAt":"2026-01-02T03:04:05Z"}}
                """);
    }

    @Test
    void loginRequest_roundTrips() throws Exception {
        assertRoundTrip(LoginRequest.class, """
                {"email":"a@example.com","password":"secret-1"}
                """);
    }

    @Test
    void refreshTokenRequest_roundTrips() throws Exception {
        assertRoundTrip(RefreshTokenRequest.class, """
                {"refreshToken":"refresh-2"}
                """);
    }

    @Test
    void registerRequest_roundTrips() throws Exception {
        assertRoundTrip(RegisterRequest.class, """
                {"email":"a@example.com","password":"secret-1","fullName":"Ada Lovelace"}
                """);
    }

    @Test
    void userResponse_roundTrips() throws Exception {
        assertRoundTrip(UserResponse.class, """
                {"id":"00000000-0000-0000-0000-000000000001","email":"a@example.com","fullName":"Ada Lovelace",
                 "roles":["DEALER_STAFF"],"createdAt":"2026-01-02T03:04:05Z"}
                """);
    }

    // --- command ---

    @Test
    void commandResponse_roundTrips() throws Exception {
        assertRoundTrip(CommandResponse.class, """
                {"id":"00000000-0000-0000-0000-000000000001","vehicleId":"00000000-0000-0000-0000-000000000002",
                 "issuedByUserId":"00000000-0000-0000-0000-000000000003","type":"REMOTE_START","status":"ACKNOWLEDGED",
                 "requestedAt":"2026-01-02T03:04:05Z","sentAt":"2026-01-02T03:04:06Z","resolvedAt":"2026-01-02T03:04:07Z",
                 "resultMessage":"Engine started","idempotencyKey":"key-4"}
                """);
    }

    @Test
    void issueCommandRequest_roundTrips() throws Exception {
        assertRoundTrip(IssueCommandRequest.class, """
                {"type":"FLASH_LIGHTS","idempotencyKey":"key-4"}
                """);
    }

    // --- common ---

    @Test
    void errorResponse_roundTrips() throws Exception {
        assertRoundTrip(ErrorResponse.class, """
                {"status":409,"error":"Conflict","message":"Already exists","timestamp":"2026-01-02T03:04:05",
                 "details":["vin: taken","model: blank"]}
                """);
    }

    // --- messaging (Kafka) ---

    @Test
    void commandResultEvent_roundTrips() throws Exception {
        assertRoundTrip(CommandResultEvent.class, """
                {"commandId":"00000000-0000-0000-0000-000000000001","vehicleId":"00000000-0000-0000-0000-000000000002",
                 "status":"COMPLETED","resultMessage":"Doors locked","timestamp":"2026-01-02T03:04:05Z"}
                """);
    }

    @Test
    void otaInstructionEvent_roundTrips() throws Exception {
        assertRoundTrip(OtaInstructionEvent.class, """
                {"updateStatusId":"00000000-0000-0000-0000-000000000001","vehicleId":"00000000-0000-0000-0000-000000000002",
                 "campaignId":"00000000-0000-0000-0000-000000000003","targetVersionId":"00000000-0000-0000-0000-000000000004",
                 "targetVersionLabel":"v2.1.0","packageUrl":"https://example.com/v2.1.0.bin","checksum":"sha256:abc"}
                """);
    }

    @Test
    void otaUpdateProgressEvent_roundTrips() throws Exception {
        assertRoundTrip(OtaUpdateProgressEvent.class, """
                {"updateStatusId":"00000000-0000-0000-0000-000000000001","vehicleId":"00000000-0000-0000-0000-000000000002",
                 "campaignId":"00000000-0000-0000-0000-000000000003","status":"INSTALLING","progressPercent":42,
                 "errorMessage":"retrying","timestamp":"2026-01-02T03:04:05Z"}
                """);
    }

    @Test
    void vehicleCommandEvent_roundTrips() throws Exception {
        assertRoundTrip(VehicleCommandEvent.class, """
                {"commandId":"00000000-0000-0000-0000-000000000001","vehicleId":"00000000-0000-0000-0000-000000000002",
                 "type":"UNLOCK","issuedAt":"2026-01-02T03:04:05Z","idempotencyKey":"key-4"}
                """);
    }

    @Test
    void vehicleHeartbeatEvent_roundTrips() throws Exception {
        assertRoundTrip(VehicleHeartbeatEvent.class, """
                {"vehicleId":"00000000-0000-0000-0000-000000000002","vin":"WVWZZZ1JZXW000001","state":"SLEEPING",
                 "timestamp":"2026-01-02T03:04:05Z"}
                """);
    }

    @Test
    void vehicleTelemetryEvent_roundTrips() throws Exception {
        assertRoundTrip(VehicleTelemetryEvent.class, """
                {"vehicleId":"00000000-0000-0000-0000-000000000002","batteryOrFuelLevel":81.5,"odometerKm":12345.6,
                 "engineTempC":90.25,"lat":52.1,"lng":4.3,"timestamp":"2026-01-02T03:04:05Z"}
                """);
    }

    @Test
    void commandResultEvent_ignoresUnknownField() throws Exception {
        assertReadsAs(CommandResultEvent.class, """
                {"commandId":"00000000-0000-0000-0000-000000000001","vehicleId":"00000000-0000-0000-0000-000000000002",
                 "status":"FAILED","resultMessage":"timeout","timestamp":"2026-01-02T03:04:05Z","futureField":1}
                """, """
                {"commandId":"00000000-0000-0000-0000-000000000001","vehicleId":"00000000-0000-0000-0000-000000000002",
                 "status":"FAILED","resultMessage":"timeout","timestamp":"2026-01-02T03:04:05Z"}
                """);
    }

    @Test
    void otaUpdateProgressEvent_ignoresUnknownField() throws Exception {
        assertReadsAs(OtaUpdateProgressEvent.class, """
                {"updateStatusId":"00000000-0000-0000-0000-000000000001","vehicleId":"00000000-0000-0000-0000-000000000002",
                 "campaignId":"00000000-0000-0000-0000-000000000003","status":"FAILED","progressPercent":7,
                 "errorMessage":"bad checksum","timestamp":"2026-01-02T03:04:05Z","futureField":1}
                """, """
                {"updateStatusId":"00000000-0000-0000-0000-000000000001","vehicleId":"00000000-0000-0000-0000-000000000002",
                 "campaignId":"00000000-0000-0000-0000-000000000003","status":"FAILED","progressPercent":7,
                 "errorMessage":"bad checksum","timestamp":"2026-01-02T03:04:05Z"}
                """);
    }

    @Test
    void vehicleTelemetryEvent_ignoresUnknownField() throws Exception {
        assertReadsAs(VehicleTelemetryEvent.class, """
                {"vehicleId":"00000000-0000-0000-0000-000000000002","batteryOrFuelLevel":81.5,"odometerKm":12345.6,
                 "engineTempC":90.25,"lat":52.1,"lng":4.3,"timestamp":"2026-01-02T03:04:05Z","futureField":1}
                """, """
                {"vehicleId":"00000000-0000-0000-0000-000000000002","batteryOrFuelLevel":81.5,"odometerKm":12345.6,
                 "engineTempC":90.25,"lat":52.1,"lng":4.3,"timestamp":"2026-01-02T03:04:05Z"}
                """);
    }

    @Test
    void vehicleHeartbeatEvent_ignoresUnknownField() throws Exception {
        assertReadsAs(VehicleHeartbeatEvent.class, """
                {"vehicleId":"00000000-0000-0000-0000-000000000002","vin":"WVWZZZ1JZXW000001","state":"ONLINE",
                 "timestamp":"2026-01-02T03:04:05Z","futureField":1}
                """, """
                {"vehicleId":"00000000-0000-0000-0000-000000000002","vin":"WVWZZZ1JZXW000001","state":"ONLINE",
                 "timestamp":"2026-01-02T03:04:05Z"}
                """);
    }

    // --- ota ---

    @Test
    void campaignResponse_roundTrips() throws Exception {
        assertRoundTrip(CampaignResponse.class, """
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
                 "totalVehiclesTargeted":9,"vehiclesInstalled":4,"vehiclesFailed":2}
                """);
    }

    @Test
    void createCampaignRequest_roundTrips() throws Exception {
        assertRoundTrip(CreateCampaignRequest.class, """
                {"softwareVersionId":"00000000-0000-0000-0000-000000000004","targetModel":"Model X",
                 "failureThresholdPercent":12.5,"stagePercentages":[5,25,100]}
                """);
    }

    @Test
    void createCampaignRequest_missingThreshold_defaultsTo10() throws Exception {
        assertReadsAs(CreateCampaignRequest.class, """
                {"softwareVersionId":"00000000-0000-0000-0000-000000000004","targetModel":"Model X"}
                """, """
                {"softwareVersionId":"00000000-0000-0000-0000-000000000004","targetModel":"Model X",
                 "failureThresholdPercent":10.0,"stagePercentages":null}
                """);
    }

    @Test
    void createSoftwareVersionRequest_roundTrips() throws Exception {
        assertRoundTrip(CreateSoftwareVersionRequest.class, """
                {"versionLabel":"v2.1.0","releaseNotes":"Fixes","checksum":"sha256:abc",
                 "packageUrl":"https://example.com/v2.1.0.bin","mandatory":true}
                """);
    }

    @Test
    void createSoftwareVersionRequest_missingMandatory_isFalse() throws Exception {
        assertReadsAs(CreateSoftwareVersionRequest.class, """
                {"versionLabel":"v2.1.0","checksum":"sha256:abc","packageUrl":"https://example.com/v2.1.0.bin"}
                """, """
                {"versionLabel":"v2.1.0","releaseNotes":null,"checksum":"sha256:abc",
                 "packageUrl":"https://example.com/v2.1.0.bin","mandatory":false}
                """);
    }

    @Test
    void rolloutStageResponse_roundTrips() throws Exception {
        assertRoundTrip(RolloutStageResponse.class, """
                {"id":"00000000-0000-0000-0000-000000000005","percentage":25,"status":"SKIPPED",
                 "startedAt":"2026-01-02T03:04:06Z","completedAt":"2026-01-02T03:04:07Z"}
                """);
    }

    @Test
    void softwareVersionResponse_roundTrips() throws Exception {
        assertRoundTrip(SoftwareVersionResponse.class, """
                {"id":"00000000-0000-0000-0000-000000000004","versionLabel":"v2.1.0","releaseNotes":"Fixes",
                 "checksum":"sha256:abc","packageUrl":"https://example.com/v2.1.0.bin","mandatory":true,
                 "createdAt":"2026-01-01T00:00:00Z"}
                """);
    }

    @Test
    void vehicleUpdateStatusResponse_roundTrips() throws Exception {
        assertRoundTrip(VehicleUpdateStatusResponse.class, """
                {"id":"00000000-0000-0000-0000-000000000001","vehicleId":"00000000-0000-0000-0000-000000000002",
                 "vin":"WVWZZZ1JZXW000001","campaignId":"00000000-0000-0000-0000-000000000003",
                 "targetVersionId":"00000000-0000-0000-0000-000000000004","targetVersionLabel":"v2.1.0",
                 "status":"ROLLED_BACK","progressPercent":63,"startedAt":"2026-01-02T03:04:05Z",
                 "completedAt":"2026-01-02T03:04:06Z","errorMessage":"boot loop"}
                """);
    }

    // --- telemetry ---

    @Test
    void ingestTelemetryRequest_roundTrips() throws Exception {
        assertRoundTrip(IngestTelemetryRequest.class, """
                {"batteryOrFuelLevel":81.5,"odometerKm":12345.6,"engineTempC":90.25,"lat":52.1,"lng":4.3}
                """);
    }

    @Test
    void telemetrySnapshotResponse_roundTrips() throws Exception {
        assertRoundTrip(TelemetrySnapshotResponse.class, """
                {"id":"00000000-0000-0000-0000-000000000001","vehicleId":"00000000-0000-0000-0000-000000000002",
                 "recordedAt":"2026-01-02T03:04:05Z","batteryOrFuelLevel":81.5,"odometerKm":12345.6,
                 "engineTempC":90.25,"lat":52.1,"lng":4.3}
                """);
    }

    // --- vehicle ---

    @Test
    void authorizeUserRequest_roundTrips() throws Exception {
        assertRoundTrip(AuthorizeUserRequest.class, """
                {"userEmail":"family@example.com"}
                """);
    }

    @Test
    void createVehicleRequest_roundTrips() throws Exception {
        assertRoundTrip(CreateVehicleRequest.class, """
                {"vin":"WVWZZZ1JZXW000001","model":"Model X","modelYear":2024}
                """);
    }

    @Test
    void vehicleResponse_roundTrips() throws Exception {
        assertRoundTrip(VehicleResponse.class, """
                {"id":"00000000-0000-0000-0000-000000000002","vin":"WVWZZZ1JZXW000001","model":"Model X",
                 "modelYear":2024,"ownerId":"00000000-0000-0000-0000-000000000003","ownerEmail":"owner@example.com",
                 "authorizedUserIds":["00000000-0000-0000-0000-000000000007"],"connectivityState":"OFFLINE",
                 "currentSoftwareVersion":"v2.0.0","lastSeenAt":"2026-01-02T03:04:05Z","batteryOrFuelLevel":81.5,
                 "odometerKm":12345.6,"lastLat":52.1,"lastLng":4.3,"createdAt":"2026-01-01T00:00:00Z"}
                """);
    }
}
