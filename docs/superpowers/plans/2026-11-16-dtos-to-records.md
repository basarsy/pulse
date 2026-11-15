# DTOs to Records Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 25 hand-written DTO classes with Java records without changing a byte of JSON.

**Architecture:** First pin today's JSON with tests that pass on the old classes: a round-trip test for every DTO, and a mapping test for each of the six service mappers. Then convert module by module. The pinning tests must stay unchanged and green throughout.

**Tech Stack:** Java 21, Spring Boot 3.3.5 (Jackson 2.17), JUnit 5, AssertJ, JSONAssert, Mockito.

**Spec:** `docs/superpowers/specs/2026-11-16-dtos-to-records-design.md`

## Global Constraints

- Scope: the 25 data classes under `pulse-monolith/src/main/java/com/pulse/connected/*/dto/`. Do not touch `StagePercentagesValidator`, `StrictIntegerDeserializer`, `ValidStagePercentages`, or the JPA entities.
- No Lombok, no new dependencies.
- Record component order = the current field declaration order (the existing all-args constructors already use this order).
- JSON field names stay as they are; `isMandatory()` becomes component `mandatory` (same JSON name).
- No defensive list or set copies.
- Constructor calls with more than 4 arguments: one argument per line.
- The pinning tests from Tasks 1–2 are not edited after Task 2's commit.

## Review Focus

- **Explicit `"failureThresholdPercent": null` in a create-campaign body.** It used to stay null in the DTO and become 10.0 in the service; now it becomes 10.0 in the compact constructor. The campaign still ends up with 10.0. A test in Task 5 creates a campaign from that body and asserts 10.0.
- **`stagePercentages` keeps strict integers.** `@JsonDeserialize(contentUsing = StrictIntegerDeserializer.class)` must apply on the record component; `[5.5, 100]` is still a 400. This is covered by the existing `RolloutCampaignControllerValidationTest`, which must stay green.
- **Bean Validation on record components.** `@NotBlank`, `@Email` and the like must still fire through `@Valid` in the controllers. Covered by the existing controller/validation tests (`IssueCommandRequestTest`, `CreateCampaignRequestTest`, `RolloutCampaignControllerValidationTest`), adjusted only for constructor or accessor syntax.
- **Kafka consumers deserialize records** (`CommandResultEvent`, `OtaUpdateProgressEvent`, `VehicleTelemetryEvent`, `VehicleHeartbeatEvent`) from producer JSON that may have unknown or missing fields. The round-trip test covers missing fields. Unknown fields keep Boot's default `FAIL_ON_UNKNOWN_PROPERTIES=false`; one round-trip case per event includes an extra field.
- **`VehicleResponse.authorizedUserIds` is a `Set`.** Fixtures use a single element so the JSON order is deterministic.

---

### Task 1: Round-trip JSON contract tests for every DTO

**Files:**
- Create: `pulse-monolith/src/test/java/com/pulse/connected/DtoJsonContractTest.java`

**Interfaces:**
- Produces: nothing used by main code.

- [ ] **Step 1: Write the test.** `@JsonTest class DtoJsonContractTest` with `@Autowired ObjectMapper json` (the Boot-configured mapper) and a helper:

```java
private void assertRoundTrip(Class<?> type, String expectedJson) throws Exception {
    Object value = json.readValue(expectedJson, type);
    JSONAssert.assertEquals(expectedJson, json.writeValueAsString(value), JSONCompareMode.STRICT);
}
```

There is one `@Test` per DTO (25 in all), named `<dto>_roundTrips`. Each one sets every field to a distinct non-default value as a Java text block: different UUIDs, different numbers, timestamps in ISO-8601, enums set to non-first values, and nested `softwareVersion` and `stages` filled in. Additional tests:
  - `createCampaignRequest_missingThreshold_defaultsTo10`: read `{"softwareVersionId":"…","targetModel":"X"}` and assert the serialized output has `"failureThresholdPercent":10.0`.
  - `createSoftwareVersionRequest_missingMandatory_isFalse`: same pattern for `"mandatory":false`.
  - `<event>_ignoresUnknownField` for each of the four Kafka events listed in Review Focus: the input has an extra field `"futureField":1`, and the output equals the input without it.

  Do not test explicit `null` threshold here (see Review Focus).

- [ ] **Step 2: Run it on the old classes.**
Run: `./mvnw -q -pl pulse-monolith test -Dtest=DtoJsonContractTest`
Expected: PASS. If a case fails, the fixture is wrong (today's code is the reference); fix the fixture, not the DTO.

- [ ] **Step 3: Commit** `test: pin the JSON of every DTO before the records refactor`

### Task 2: Mapper tests for the six response mappers

**Files:**
- Create: `pulse-monolith/src/test/java/com/pulse/connected/ResponseMappingContractTest.java`

**Interfaces:**
- Consumes: `CommandService.mapToResponse(RemoteCommand)`, `RolloutCampaignService.mapToCampaignResponse(RolloutCampaign)`, `RolloutCampaignService.mapToUpdateStatusResponse(VehicleUpdateStatus)`, `SoftwareVersionService.mapToResponse(SoftwareVersion)`, `VehicleService.mapToResponse(Vehicle)`, `TelemetryService.getLatestSnapshot(UUID)` (its mapper is private; go through this method with a mocked `TelemetrySnapshotRepository.findTopByVehicleIdOrderByRecordedAtDesc` and `VehicleRepository.existsById` → true).

- [ ] **Step 1: Write the test.** Use `@JsonTest` with `@Autowired ObjectMapper json`, and construct each service with `new` and `Mockito.mock(...)` dependencies. `RolloutCampaignService` gets a real `new SoftwareVersionService(mock)`; stub `vehicleUpdateStatusRepository.countByStageAndStatus(campaignId)` so that total, installed and failed are three different numbers, and the meter registry is a `SimpleMeterRegistry`. Build each source entity with its builder and give every field a distinct value. In particular, give two neighbouring same-type fields different values:
  - `CampaignResponse`: total 9, installed 4, failed 2;
  - `VehicleResponse`: battery 81.5, odometer 12345.6, lat 52.1, lng 4.3, plus a single authorized user;
  - `TelemetrySnapshotResponse`: battery, odometer, engine temp, lat and lng all different;
  - `VehicleUpdateStatusResponse`: `vehicleId`, `campaignId` and `targetVersionId` all different.

  There is one test per mapper, named `<response>_mapsEveryField`, and each asserts `JSONAssert.assertEquals(expectedTextBlock, json.writeValueAsString(response), STRICT)`.

- [ ] **Step 2: Run it.**
Run: `./mvnw -q -pl pulse-monolith test -Dtest=ResponseMappingContractTest`
Expected: PASS on the old classes.

- [ ] **Step 3: Commit** `test: pin each response mapper field by field`

### Task 3: Convert auth, common, vehicle, telemetry DTOs

**Files:**
- Modify: `auth/dto/{AuthResponse,LoginRequest,RefreshTokenRequest,RegisterRequest,UserResponse}.java`, `common/dto/ErrorResponse.java`, `vehicle/dto/{AuthorizeUserRequest,CreateVehicleRequest,VehicleResponse}.java`, `telemetry/dto/{IngestTelemetryRequest,TelemetrySnapshotResponse}.java`, `messaging/dto/VehicleTelemetryEvent.java`, `messaging/dto/VehicleHeartbeatEvent.java`
- Modify: their call sites in `src/main` and `src/test`. Find them with the compiler (`./mvnw -q -pl pulse-monolith test-compile`).

- [ ] **Step 1: Rewrite each as `public record X(...)`**, with validation and Jackson annotations moved onto the components. Delete the builders, getters, setters and the no-arg constructor.
- [ ] **Step 2: Fix call sites**: `getX()`/`isX()` become `x()`, and `X.builder()…build()` becomes `new X(...)` (one argument per line when there are more than 4).
- [ ] **Step 3: Run the module's tests and both contract tests.**
Run: `./mvnw -q -pl pulse-monolith test -Dtest='DtoJsonContractTest,ResponseMappingContractTest,Auth*Test,Vehicle*Test,Telemetry*Test,GlobalExceptionHandlerTest'`
Expected: PASS, with the contract tests unchanged (`git diff --stat` shows no change under those two files).
- [ ] **Step 4: Commit** `refactor: auth, vehicle, telemetry and error DTOs become records`

### Task 4: Convert command and remaining messaging DTOs

**Files:**
- Modify: `command/dto/{CommandResponse,IssueCommandRequest}.java`, `messaging/dto/{CommandResultEvent,VehicleCommandEvent,OtaInstructionEvent,OtaUpdateProgressEvent}.java` and their call sites (including `LiveUpdatesAcrossInstancesTest`).

- [ ] **Step 1–2:** Same as Task 3.
- [ ] **Step 3: Run** `./mvnw -q -pl pulse-monolith test -Dtest='DtoJsonContractTest,ResponseMappingContractTest,Command*Test,IssueCommandRequestTest,LiveUpdates*Test'`. Expected: PASS.
- [ ] **Step 4: Commit** `refactor: command DTOs and Kafka events become records`

### Task 5: Convert OTA DTOs

**Files:**
- Modify: `ota/dto/{CampaignResponse,CreateCampaignRequest,CreateSoftwareVersionRequest,RolloutStageResponse,SoftwareVersionResponse,VehicleUpdateStatusResponse}.java` and their call sites.
- Test: `pulse-monolith/src/test/java/com/pulse/connected/ota/RolloutCampaignServiceTest.java`

- [ ] **Step 1: Add the Review Focus test** `createCampaign_explicitNullThreshold_uses10` to `RolloutCampaignServiceTest`. It reads `{"softwareVersionId":"<id>","targetModel":"Model X","failureThresholdPercent":null}` with an `ObjectMapper`, calls `createCampaign`, and asserts that the saved campaign's `getFailureThresholdPercent()` is `10.0`. Run it; expected PASS on the old classes (the service defaults the value). Commit it with this task.
- [ ] **Step 2: Convert.** `CreateCampaignRequest` gets a compact constructor: `if (failureThresholdPercent == null) failureThresholdPercent = 10.0;`. Keep the service's own null check; it is harmless, and removing it is out of scope. `CreateSoftwareVersionRequest.mandatory` stays a primitive `boolean`.
- [ ] **Step 3: Fix call sites** as in Task 3.
- [ ] **Step 4: Run** `./mvnw -q -pl pulse-monolith test -Dtest='DtoJsonContractTest,ResponseMappingContractTest,Rollout*Test,CampaignProgressTest,UpdateStatusTransitionsTest,CreateCampaignRequestTest,SoftwareVersion*Test'`. Expected: PASS.
- [ ] **Step 5: Commit** `refactor: OTA DTOs become records`

### Task 6: Full verification

- [ ] **Step 1: No leftovers.** Run `grep -rlE "builder\(\)|public \w+ (get|set|is)[A-Z]" pulse-monolith/src/main/java/com/pulse/connected/*/dto/`. Expected: no output.
- [ ] **Step 2: Full suite with Docker running.** Run `./mvnw -q verify`. Expected: BUILD SUCCESS. The test count is the baseline (164) plus the new tests.
- [ ] **Step 3: Record the size change.** Run `git diff --stat main -- pulse-monolith/src/main` for the PR description.
