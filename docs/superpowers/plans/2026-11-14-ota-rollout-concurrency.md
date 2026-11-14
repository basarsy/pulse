# OTA Rollout Concurrency & Cost Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rollout stage evaluation stays correct with parallel progress events and costs a constant number of queries per event, independent of fleet size.

**Architecture:** One grouped summary query (`stageIndex`, `status`, count) replaces loading every update row and the 4 COUNT queries; a `CampaignProgress` value answers every stage and response question. Stage start selects only the vehicles to instruct, in SQL. Each progress event and each operator action locks its campaign row (`PESSIMISTIC_WRITE`), so events for one campaign serialise while different campaigns stay parallel. The OTA progress listener then runs with 3 threads.

**Tech Stack:** Java 21, Spring Boot 3.3, Spring Data JPA / Hibernate 6 (JPQL constructor expressions, `@Lock`), Spring Kafka, Flyway, JUnit 5 + Mockito + AssertJ, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-11-14-ota-rollout-concurrency-design.md`

## Global Constraints

- Rollout rules unchanged:
  - stages are cumulative shares of the model's fleet, `targetCount = ceil(fleet × pct / 100)`, taken in VIN order;
  - vehicles already targeted in the campaign, or already on the target version, are skipped (but count towards the share);
  - the failure rate is per stage, `failed = FAILED + ROLLED_BACK`, and the campaign auto-pauses when the rate is `> failureThresholdPercent`;
  - a stage is finished when all its updates are `INSTALLED`, `FAILED` or `ROLLED_BACK` (a stage with no updates counts as finished).
- `CampaignResponse` fields and values unchanged: `totalVehiclesTargeted`, `vehiclesInstalled`, `vehiclesFailed = FAILED + ROLLED_BACK`.
- One instance only. No changes to the other listeners' concurrency, the schedulers or k8s.
- Config key `pulse.ota.progress-consumer-concurrency`, default `3`. Index name `idx_vehicle_update_status_campaign_stage_status`.
- Commits as `basarsy <basarsoyturk03@gmail.com>`, no Claude/Anthropic attribution.
- Backend tests: `./mvnw -B -ntp -pl pulse-monolith test -Dtest=<Class>` from the repo root. Integration tests need Docker.

## Review Focus

1. `SELECT … FOR UPDATE` on `RolloutCampaign` must not join its EAGER `stages` collection: Postgres rejects `FOR UPDATE` on the nullable side of an outer join. → Task 3's integration test goes through the locked lookup.
2. A model with no vehicles: `targetCount = 0` must not query a VIN at offset −1, and the stage must still complete. → Task 2 test.
3. A stage where every vehicle in scope is already on the target version must still advance (no updates, so the stage counts as finished). → Task 2 test.
4. A progress event for an unknown `updateStatusId` → `ResourceNotFoundException`, as today. → Task 3 test.
5. Dashboard numbers: `vehiclesFailed` must include `ROLLED_BACK`, and stage totals must not leak across stages. → Task 1 test.

---

### Task 1: Summary query and `CampaignProgress`

**Files:**
- Create: `pulse-monolith/src/main/java/com/pulse/connected/ota/StageStatusCount.java`
- Create: `pulse-monolith/src/main/java/com/pulse/connected/ota/CampaignProgress.java`
- Modify: `pulse-monolith/src/main/java/com/pulse/connected/ota/VehicleUpdateStatusRepository.java` (add the summary query; remove `countByCampaignIdAndStatus` and `countTotalByCampaignId`)
- Modify: `pulse-monolith/src/main/java/com/pulse/connected/ota/RolloutCampaignService.java` (`evaluateCampaignStage`, `updatesInCurrentStage` removed, `advanceIfStageFinished`, `mapToCampaignResponse`)
- Create: `pulse-monolith/src/main/resources/db/migration/V7__vehicle_update_status_progress_index.sql`
- Test: `pulse-monolith/src/test/java/com/pulse/connected/ota/CampaignProgressTest.java`
- Test: `pulse-monolith/src/test/java/com/pulse/connected/ota/RolloutCampaignServiceTest.java`
- Create: `pulse-monolith/src/test/java/com/pulse/connected/RolloutQueriesTest.java` (`@Transactional`, extends `AbstractIntegrationTest`)

**Interfaces:**
- Produces: `public record StageStatusCount(int stageIndex, UpdateStatus status, long count)`.
- Produces: `VehicleUpdateStatusRepository.countByStageAndStatus(UUID campaignId): List<StageStatusCount>`. JPQL: `SELECT new com.pulse.connected.ota.StageStatusCount(u.stageIndex, u.status, COUNT(u)) FROM VehicleUpdateStatus u WHERE u.campaign.id = :campaignId GROUP BY u.stageIndex, u.status`.
- Produces: `final class CampaignProgress` with
  - `static CampaignProgress of(List<StageStatusCount> rows)`;
  - campaign-wide: `long total()`, `long installed()`, `long failed()`;
  - per stage: `long stageTotal(int stageIndex)`, `long stageFailed(int stageIndex)`, `long stageFinished(int stageIndex)`.
- Produces: private `CampaignProgress progressOf(RolloutCampaign)` in the service. One call per evaluation and one per `mapToCampaignResponse`.

Migration V7:
```sql
CREATE INDEX idx_vehicle_update_status_campaign_stage_status
    ON vehicle_update_status (campaign_id, stage_index, status);
```

- [ ] **Step 1: Write the failing tests.**
  - `CampaignProgressTest`. Rows: stage 0 `INSTALLED`×3, `FAILED`×1; stage 1 `ROLLED_BACK`×2, `DOWNLOADING`×4.
    - `campaignWideCounts`: `total()==10`, `installed()==3`, `failed()==3`.
    - `perStageCounts`: `stageTotal(0)==4`, `stageFailed(0)==1`, `stageFinished(0)==4`; `stageTotal(1)==6`, `stageFailed(1)==2`, `stageFinished(1)==2`.
    - `emptyStage_IsZero`: `stageTotal(5)==0`, `stageFinished(5)==0`.
  - `RolloutCampaignServiceTest`: in the tests that currently stub `findAllByCampaignId` for evaluation (`failureRate_IsEvaluatedPerStage`, `handleVehicleUpdateProgress_AutoPausesWhenFailureThresholdExceeded`) and the count methods, stub `countByStageAndStatus(campaign.getId())` instead, with the equivalent rows. Keep their assertions. Add:
    - `progressEvent_DoesNotLoadAllUpdateRows`: after `handleVehicleUpdateProgress(...)`, `verify(vehicleUpdateStatusRepository, never()).findAllByCampaignId(any())`.
  - `RolloutQueriesTest.summary_GroupsByStageAndStatus`: save a user, a software version, a campaign and 3 vehicles; save updates (stage 0 `INSTALLED`, stage 0 `FAILED`, stage 1 `INSTALLED`). `CampaignProgress.of(repo.countByStageAndStatus(id))` has `stageTotal(0)==2`, `stageFailed(0)==1`, `stageTotal(1)==1`, `installed()==2`.

- [ ] **Step 2: Run tests to verify they fail**
  Run: `./mvnw -B -ntp -pl pulse-monolith test -Dtest='CampaignProgressTest,RolloutCampaignServiceTest'`
  Expected: compilation failure (`StageStatusCount`, `CampaignProgress` and `countByStageAndStatus` don't exist).

- [ ] **Step 3: Implement** the record, `CampaignProgress`, the query and the migration. In the service:
  - `evaluateCampaignStage` gets `failedCount`/`total` from `progressOf(campaign)` for `currentStageIndex`;
  - `advanceIfStageFinished` uses `stageFinished == stageTotal`;
  - `mapToCampaignResponse` takes its three numbers from the same value.

  `getCampaignVehicleStatuses` keeps `findAllByCampaignId`, because it is the listing endpoint.

- [ ] **Step 4: Run tests to verify they pass**
  Run: `./mvnw -B -ntp -pl pulse-monolith test -Dtest='CampaignProgressTest,RolloutCampaignServiceTest,RolloutQueriesTest,ApplicationSmokeTest'`
  Expected: PASS. The smoke test proves V7 applies and the JPQL constructor expression validates.

- [ ] **Step 5: Commit**
  ```bash
  git add pulse-monolith/src
  git commit -m "perf(ota): one summary query per progress event instead of loading every update"
  ```

---

### Task 2: Stage start selects only the vehicles it instructs

**Files:**
- Modify: `pulse-monolith/src/main/java/com/pulse/connected/ota/VehicleUpdateStatusRepository.java`
- Modify: `pulse-monolith/src/main/java/com/pulse/connected/ota/RolloutCampaignService.java` (`executeActiveStage`)
- Test: `pulse-monolith/src/test/java/com/pulse/connected/RolloutQueriesTest.java`
- Test: `pulse-monolith/src/test/java/com/pulse/connected/ota/RolloutCampaignServiceTest.java`

**Interfaces:**
- Consumes: `progressOf` from Task 1 (the "nothing new to instruct" path still calls `advanceIfStageFinished`).
- Produces, on `VehicleUpdateStatusRepository` (JPQL over `Vehicle`, model matched with `UPPER(v.model) = UPPER(:model)`):
  - `long countVehiclesOfModel(String model)`;
  - `List<String> findVinsOfModel(String model, Pageable page)`, ordered by `v.vin` (the cutoff VIN is `PageRequest.of(targetCount - 1, 1)`);
  - `List<Vehicle> findVehiclesToInstruct(UUID campaignId, String model, String lastVin, String targetVersionLabel)`: `v.vin <= :lastVin`, `v.currentSoftwareVersion <> :targetVersionLabel`, `NOT EXISTS (SELECT 1 FROM VehicleUpdateStatus u WHERE u.campaign.id = :campaignId AND u.vehicle = v)`, `ORDER BY v.vin`.
- `VehicleRepository.findAllByModelIgnoreCaseOrderByVinAsc` is no longer used by OTA. Remove it if nothing else uses it.

- [ ] **Step 1: Write the failing tests.** In `RolloutQueriesTest`, using a unique model per test (`"Model-" + UUID`) so that only the test's own vehicles match:
  - `vehiclesToInstruct_FirstNByVin`: VINs `A…`, `B…`, `C…`, `D…`; cutoff at position 2 → `findVinsOfModel(model, PageRequest.of(1,1))` is `B…`; `findVehiclesToInstruct(…, "B…", label)` returns A, B in that order.
  - `vehiclesToInstruct_SkipsAlreadyTargetedAndOnTargetVersion`: A has an update in the campaign, and B is on `label` → with cutoff `D…` the result is C, D.
  - `vehiclesOfModel_MatchCaseInsensitively`: vehicles saved as `"bmw ix"`, counted as `countVehiclesOfModel("BMW IX") == n`.

  In `RolloutCampaignServiceTest`, rework `startCampaign_DispatchesFirstStageToItsShareOfTheFleet`, `laterStage_OnlyInstructsVehiclesNotTargetedInEarlierStages` and `vehiclesAlreadyOnTargetVersion_AreNotInstructed` to stub the three new methods. Keep their assertions on what is saved and published. Assert that 2 vehicles at 50% ask for the cutoff at `PageRequest.of(0, 1)`. Add:
  - `emptyFleet_StageCompletesWithoutQueryingAVin`: `countVehiclesOfModel` → 0. `startCampaign` completes every stage (the campaign ends `COMPLETED`) and `verify(repo, never()).findVinsOfModel(any(), any())`.

- [ ] **Step 2: Run tests to verify they fail**
  Run: `./mvnw -B -ntp -pl pulse-monolith test -Dtest='RolloutCampaignServiceTest'`
  Expected: compilation failure (new repository methods missing).

- [ ] **Step 3: Implement** `executeActiveStage`:
  - `fleet = countVehiclesOfModel`; `targetCount = ceil(fleet × pct / 100)`;
  - if `targetCount == 0`, there is nothing to instruct;
  - otherwise `lastVin = findVinsOfModel(model, PageRequest.of(targetCount - 1, 1)).get(0)` and `newlyTargeted = findVehiclesToInstruct(...)`.

  The rest (saving update rows, publishing instructions, the empty-path advance, the log line) is unchanged.

- [ ] **Step 4: Run tests to verify they pass**
  Run: `./mvnw -B -ntp -pl pulse-monolith test -Dtest='RolloutCampaignServiceTest,RolloutQueriesTest,ModularityTests'`
  Expected: PASS.

- [ ] **Step 5: Commit**
  ```bash
  git add pulse-monolith/src
  git commit -m "perf(ota): start stages without loading the whole fleet"
  ```

---

### Task 3: Lock the campaign per event; 3 consumer threads

**Files:**
- Modify: `pulse-monolith/src/main/java/com/pulse/connected/ota/RolloutCampaignRepository.java`
- Modify: `pulse-monolith/src/main/java/com/pulse/connected/ota/VehicleUpdateStatusRepository.java`
- Modify: `pulse-monolith/src/main/java/com/pulse/connected/ota/RolloutCampaignService.java` (`handleVehicleUpdateProgress`, `startCampaign`, `pauseCampaign`, `abortCampaign`)
- Modify: `pulse-monolith/src/main/java/com/pulse/connected/ota/OtaProgressKafkaConsumer.java`
- Test: `pulse-monolith/src/test/java/com/pulse/connected/ota/RolloutCampaignServiceTest.java`
- Create: `pulse-monolith/src/test/java/com/pulse/connected/RolloutConcurrencyTest.java` (extends `AbstractIntegrationTest`, **not** `@Transactional`)

**Interfaces:**
- Produces: `RolloutCampaignRepository.findByIdForUpdate(UUID id): Optional<RolloutCampaign>`, with `@Lock(LockModeType.PESSIMISTIC_WRITE)` and `@Query("SELECT c FROM RolloutCampaign c WHERE c.id = :id")` (no fetch join).
- Produces: `VehicleUpdateStatusRepository.findCampaignIdById(UUID updateStatusId): Optional<UUID>` (`SELECT u.campaign.id …`).
- Produces: `@KafkaListener(id = "ota-progress", …, concurrency = "${pulse.ota.progress-consumer-concurrency:3}")`.

- [ ] **Step 1: Write the failing tests.**
  - `RolloutCampaignServiceTest`:
    - `progressEvent_LocksCampaignBeforeReadingTheUpdate`: use `InOrder`. `findCampaignIdById`, then `findByIdForUpdate(campaignId)`, then `vehicleUpdateStatusRepository.findById(updateId)`.
    - `progressEvent_UnknownUpdate_NotFound`: `findCampaignIdById` empty → `ResourceNotFoundException`; `verify(campaignRepository, never()).findByIdForUpdate(any())`.
    - `operatorActions_UseTheLockedLookup`: `startCampaign`, `pauseCampaign` and `abortCampaign` each call `findByIdForUpdate`, and never `findById`.
  - `RolloutConcurrencyTest.parallelFinalEvents_AdvanceStageExactlyOnce`:
    - Setup:
      - save a user, a software version and 10 vehicles of a unique model;
      - save a campaign directly: `IN_PROGRESS`, `currentStageIndex 0`, stages 50% (`IN_PROGRESS`) and 100% (`PENDING`);
      - save `INSTALLING` updates (stage 0) for the first 5 vehicles by VIN.

      No instructions are published for these, so the simulator ignores them.
    - Submit `handleVehicleUpdateProgress(INSTALLED, 100%)` for the 5 updates from 5 threads released by one `CountDownLatch`. Collect exceptions.
    - Assert:
      - no thread threw;
      - stage 0 is `COMPLETED`;
      - `currentStageIndex == 1`;
      - the campaign has exactly 10 update rows: 5 in stage 0 and 5 in stage 1, with no duplicate insert.

      Before the lock this either stalls (stage 0 stays `IN_PROGRESS`) or double-starts (a duplicate-key exception).
    - `otaProgressListener_RunsWithThreeThreads`: `((ConcurrentMessageListenerContainer<?, ?>) registry.getListenerContainer("ota-progress")).getConcurrency() == 3`.

- [ ] **Step 2: Run tests to verify they fail**
  Run: `./mvnw -B -ntp -pl pulse-monolith test -Dtest='RolloutCampaignServiceTest,RolloutConcurrencyTest'`
  Expected: compilation failure, then (after adding only the repository methods) the unit tests fail on order and lookup. `RolloutConcurrencyTest` fails on stall or duplicate. It is probabilistic before the fix and deterministic after; the unit order test is the deterministic RED.

- [ ] **Step 3: Implement.**
  - `handleVehicleUpdateProgress`: resolve the campaign id (missing → `ResourceNotFoundException("Vehicle update status not found with ID: …")`), lock the campaign, then load and change the update row, then evaluate. Evaluate on the locked campaign instance.
  - `startCampaign`, `pauseCampaign` and `abortCampaign` load through `findByIdForUpdate` (`resumeCampaign` delegates to `startCampaign`).
  - Listener annotation as in Interfaces.

- [ ] **Step 4: Run tests to verify they pass**
  Run: `./mvnw -B -ntp -pl pulse-monolith test -Dtest='RolloutCampaignServiceTest,RolloutConcurrencyTest,KafkaRoundTripTest,LiveUpdatesWebSocketTest'`
  Expected: PASS. `KafkaRoundTripTest.stagedOtaRolloutCompletesAcrossAllStages` runs a full staged rollout through Kafka with 3 listener threads; `LiveUpdatesWebSocketTest` covers the live pushes. Re-run `RolloutConcurrencyTest` twice more; it must pass every time.

- [ ] **Step 5: Commit**
  ```bash
  git add pulse-monolith/src
  git commit -m "fix(ota): serialise progress events per campaign; 3 progress consumer threads"
  ```

---

### Final verification

- [ ] With Docker running: `./mvnw -B -ntp verify` → `BUILD SUCCESS`.
- [ ] `git log --format='%an <%ae> | %cn <%ce>%n%B' main..HEAD`: author and committer are the user, with no Claude/Anthropic mention.
