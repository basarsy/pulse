# Command Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make remote commands behave as the README promises: vehicles go offline, queued commands wait 15 min, dispatched commands time out 60s after sending, and final states stay final.

**Architecture:** A transition table guards command status changes (mirroring `ota/UpdateStatusTransitions`). A `sent_at` column lets the existing stale-command sweep apply separate queue and execution limits from config. A scheduled bulk update in the `vehicle` module marks silent vehicles `OFFLINE`, which reactivates the existing reconnect/flush path.

**Tech Stack:** Java 21, Spring Boot 3.3, Spring Data JPA, Flyway, JUnit 5 + Mockito + AssertJ, Testcontainers; React + TypeScript (Vite, Vitest).

**Spec:** `docs/superpowers/specs/2026-11-14-command-lifecycle-design.md`

## Global Constraints

- Defaults: `pulse.connectivity.offline-after=PT60S`, `pulse.connectivity.check-interval-ms=15000`, `pulse.commands.queue-expiry=PT15M`, `pulse.commands.execution-timeout=PT60S`.
- Final command states: `COMPLETED`, `FAILED`, `TIMED_OUT`, `CANCELLED`.
- Cancel message stays `"Cancelled by user"`.
- No new dependencies. Follow existing patterns (constructor injection, hand-written builders, Mockito unit tests).
- Commits as `basarsy <basarsoyturk03@gmail.com>`, no Claude/Anthropic attribution.
- Backend tests: `./mvnw -B -ntp -pl pulse-monolith test -Dtest=<Class>` (run from the repo root). Integration tests need Docker Desktop running.

## Review Focus

1. A `COMPLETED` result that arrives after the command `TIMED_OUT` or was `CANCELLED` must be ignored, not revive it. → Task 1 test.
2. A result whose `vehicleId` differs from the command's vehicle must be ignored. → Task 1 test.
3. An `ACKNOWLEDGED` result must not set `resolvedAt`; the command must still time out 60s after `sentAt`. → Task 1 and Task 2 tests.
4. A command queued for 10 min and flushed on reconnect must not be timed out on the next sweep: its `sentAt` is fresh even though `requestedAt` is old. → Task 2 integration test.
5. Rows that are `SENT`/`ACKNOWLEDGED` when V5 runs have no `sent_at`; without a backfill they could never time out. → Task 2 migration backfill.

Known, accepted edge case (not tested): if a heartbeat consumer loads a vehicle in the instant before the sweep marks it `OFFLINE`, it saves `ONLINE` without publishing `VehicleReconnectedEvent`. Commands queued in that gap expire after 15 min instead of flushing. The window is milliseconds on a vehicle that was silent for 60s.

---

### Task 1: Command status transitions and `CANCELLED`

**Files:**
- Modify: `pulse-monolith/src/main/java/com/pulse/connected/domain/CommandStatus.java` (add `CANCELLED`)
- Create: `pulse-monolith/src/main/java/com/pulse/connected/command/CommandStatusTransitions.java`
- Modify: `pulse-monolith/src/main/java/com/pulse/connected/command/CommandService.java` (`processCommandResult`, `cancelCommand`)
- Modify: `pulse-monolith/src/main/java/com/pulse/connected/command/CommandResultKafkaConsumer.java` (pass `event.getVehicleId()`)
- Create: `pulse-monolith/src/test/java/com/pulse/connected/command/CommandStatusTransitionsTest.java`
- Modify: `pulse-monolith/src/test/java/com/pulse/connected/command/CommandServiceTest.java`

**Interfaces:**
- Produces: `final class CommandStatusTransitions` (package-private) with `static boolean isFinal(CommandStatus s)` and `static boolean isAllowed(CommandStatus from, CommandStatus to)`.
- Produces: `CommandService.processCommandResult(UUID commandId, UUID vehicleId, CommandStatus resultStatus, String resultMessage)` (new `vehicleId` parameter).

Transition rules: nothing leaves a final state; `to == PENDING` is never allowed; `SENT` only from `PENDING`; `ACKNOWLEDGED` only from `SENT`; any final state from any non-final state.

- [ ] **Step 1: Write the failing tests** in `CommandStatusTransitionsTest`:
  - `finalStates_AreFinal`: `isFinal` true for `COMPLETED, FAILED, TIMED_OUT, CANCELLED`; false for `PENDING, SENT, ACKNOWLEDGED`.
  - `nothingLeavesAFinalState`: for every final `from` and every `to`, `isAllowed` is false.
  - `forwardPath_Allowed`: `PENDING→SENT`, `SENT→ACKNOWLEDGED`, `ACKNOWLEDGED→COMPLETED`, `SENT→FAILED`, `PENDING→TIMED_OUT` all true.
  - `backwardsOrSkipping_NotAllowed`: `SENT→PENDING`, `ACKNOWLEDGED→SENT`, `PENDING→ACKNOWLEDGED`, `ACKNOWLEDGED→ACKNOWLEDGED` all false.

  Add to `CommandServiceTest` (use a helper `commandFor(Vehicle, CommandStatus)` alongside `pendingCommandFor`):
  - `processCommandResult_AfterTimeout_IsIgnored`: command `TIMED_OUT`; calling with `COMPLETED` → `verify(commandRepository, never()).save(any())`, `verifyNoInteractions(webSocketService)`, status still `TIMED_OUT`.
  - `processCommandResult_AfterCancel_IsIgnored`: same with `CANCELLED`.
  - `processCommandResult_FromAnotherVehicle_IsIgnored`: `SENT` command for `onlineVehicle`, result with `offlineVehicle.getId()` → no save, status still `SENT`.
  - `processCommandResult_Acknowledged_DoesNotResolve`: `SENT` command, result `ACKNOWLEDGED` → saved with status `ACKNOWLEDGED` and `getResolvedAt()` null.
  - `processCommandResult_Completed_Resolves`: `ACKNOWLEDGED` command, result `COMPLETED` → status `COMPLETED`, `resolvedAt` not null, counter `pulse.commands.completed` is 1.
  - Change `cancelCommand_PendingCommand_SuccessAndAuditsActingUser` to expect `CommandStatus.CANCELLED` and `meterRegistry.counter("pulse.commands.cancelled").count() == 1.0`.

- [ ] **Step 2: Run tests to verify they fail**
  Run: `./mvnw -B -ntp -pl pulse-monolith test -Dtest='CommandStatusTransitionsTest,CommandServiceTest'`
  Expected: compilation failure (`CANCELLED`, `CommandStatusTransitions` and the 4-arg `processCommandResult` don't exist).

- [ ] **Step 3: Implement.**
  - Add `CANCELLED` to `CommandStatus`.
  - Write `CommandStatusTransitions` with a class Javadoc diagram, in the style of `ota/UpdateStatusTransitions`.
  - `processCommandResult`: if the command is missing, keep the current warn and return. If `vehicleId` doesn't match the command's vehicle, warn and return. If `!isAllowed(current, result)`, log at info ("Ignoring command result ... {} -> {} is not allowed") and return; no exception, so it isn't dead-lettered. Set `resolvedAt` only when `isFinal(resultStatus)`.
  - `cancelCommand`: set `CANCELLED`, increment `pulse.commands.cancelled`. Keep the `PENDING`/`SENT` precondition and message.
  - `CommandResultKafkaConsumer`: pass `event.getVehicleId()`. Run `grep -rn "processCommandResult" pulse-monolith/src` to confirm there are no other callers.

- [ ] **Step 4: Run tests to verify they pass**
  Run: the command from Step 2. Expected: `BUILD SUCCESS`, all tests in both classes pass.

- [ ] **Step 5: Commit**
  ```bash
  git add pulse-monolith/src
  git commit -m "fix(command): keep final command states final and add CANCELLED"
  ```

---

### Task 2: `sent_at` and separate queue/execution timeouts

**Files:**
- Create: `pulse-monolith/src/main/resources/db/migration/V5__command_sent_at.sql`
- Create: `pulse-monolith/src/main/java/com/pulse/connected/command/CommandTimeoutProperties.java`
- Modify: `pulse-monolith/src/main/java/com/pulse/connected/PulseApplication.java` (add `@ConfigurationPropertiesScan`)
- Modify: `pulse-monolith/src/main/java/com/pulse/connected/command/RemoteCommand.java` (field, getter/setter, builder, all-args constructor)
- Modify: `pulse-monolith/src/main/java/com/pulse/connected/command/RemoteCommandRepository.java`
- Modify: `pulse-monolith/src/main/java/com/pulse/connected/command/CommandService.java` (`dispatchToKafka`, `checkStaleCommands`, constructor)
- Modify: `pulse-monolith/src/main/java/com/pulse/connected/command/dto/CommandResponse.java` (add `sentAt`) and `mapToResponse`
- Modify: `pulse-monolith/src/test/java/com/pulse/connected/command/CommandServiceTest.java`
- Create: `pulse-monolith/src/test/java/com/pulse/connected/CommandLifecycleQueriesTest.java` (extends `AbstractIntegrationTest`)

**Interfaces:**
- Consumes: `CommandStatusTransitions` from Task 1 (not required here, but `CANCELLED` must exist).
- Produces: `@ConfigurationProperties("pulse.commands") public record CommandTimeoutProperties(@DefaultValue("PT15M") Duration queueExpiry, @DefaultValue("PT60S") Duration executionTimeout)`.
- Produces: `RemoteCommandRepository.findAllByStatusAndRequestedAtBefore(CommandStatus status, Instant cutoff)` and `findAllByStatusInAndSentAtBefore(Collection<CommandStatus> statuses, Instant cutoff)`, both returning `List<RemoteCommand>`. Remove `findStaleCommands`.
- Produces: `RemoteCommand.getSentAt()/setSentAt(Instant)`, builder `.sentAt(Instant)`, and `CommandResponse.sentAt`.
- `CommandService` constructor gains a last parameter `CommandTimeoutProperties timeouts`.

Migration V5:
```sql
ALTER TABLE remote_commands ADD COLUMN sent_at TIMESTAMP WITH TIME ZONE;
-- Commands already in flight: best available approximation, so they still time out.
UPDATE remote_commands SET sent_at = requested_at WHERE status IN ('SENT', 'ACKNOWLEDGED');
```

- [ ] **Step 1: Write the failing tests.**
  In `CommandServiceTest`, replace `@InjectMocks` with explicit construction in `setUp()`, using `new CommandTimeoutProperties(Duration.ofMinutes(15), Duration.ofSeconds(60))` as the last argument. Then add:
  - `issueCommand_OnlineVehicle_RecordsSentAt`: extend the existing online test, or add a new one, asserting the saved command's `getSentAt()` is not null.
  - `checkStaleCommands_ExpiresQueuedAfterQueueExpiry`: stub `findAllByStatusAndRequestedAtBefore(eq(PENDING), any())` to return one `PENDING` command and the other finder to return empty. After `checkStaleCommands()`, the command is `TIMED_OUT` with message `"Vehicle did not come online within 15 minutes"`, `resolvedAt` set. Capture the cutoff and assert it `isBetween(before.minus(15 min), after.minus(15 min))`, where `before`/`after` are `Instant.now()` taken around the call.
  - `checkStaleCommands_TimesOutDispatchedAfterExecutionTimeout`: stub `findAllByStatusInAndSentAtBefore(eq(Set.of(SENT, ACKNOWLEDGED)), any())` (match the collection type you choose) to return one `ACKNOWLEDGED` command. Result: `TIMED_OUT`, message `"Command execution timed out after 60 seconds"`. The cutoff is between `before.minus(60s)` and `after.minus(60s)`. Counter `pulse.commands.timed_out` is 1.

  In `CommandLifecycleQueriesTest` (mark it `@Transactional` so writes roll back and the simulator never sees them; use `@Autowired` repositories; save a `User` and a `Vehicle` directly):
  - `pendingCommand_FoundByRequestedAtCutoff`: save a `PENDING` command. It is in `findAllByStatusAndRequestedAtBefore(PENDING, now + 1h)` and not in `(PENDING, now − 1h)`.
  - `flushedCommand_UsesSentAtNotRequestedAt`: save a `SENT` command with `sentAt = now`. It is **not** in `findAllByStatusInAndSentAtBefore({SENT, ACKNOWLEDGED}, now − 1s)`, but **is** in `(…, now + 1h)`. (`requested_at` is set by `@CreationTimestamp`, so it can't be backdated; this pins that the execution query ignores it.)

- [ ] **Step 2: Run tests to verify they fail**
  Run: `./mvnw -B -ntp -pl pulse-monolith test -Dtest='CommandServiceTest'`
  Expected: compilation failure (`CommandTimeoutProperties`, the new finders and `sentAt` don't exist).

- [ ] **Step 3: Implement.**
  - Migration, record and `@ConfigurationPropertiesScan`.
  - Entity field `@Column(name = "sent_at") private Instant sentAt;`, threaded through the constructor and builder.
  - `dispatchToKafka` sets `sentAt = Instant.now()` together with `SENT`.
  - `checkStaleCommands` (keep `@Scheduled(fixedRate = 30000)`): run the two finders and expire each result through one private helper `expire(RemoteCommand, String message)` (it holds the existing save/metric/audit/WebSocket code). Build the messages from config: `"Vehicle did not come online within " + queueExpiry.toMinutes() + " minutes"` and `"Command execution timed out after " + executionTimeout.toSeconds() + " seconds"`.
  - Add `sentAt` to `CommandResponse` and `mapToResponse`.

- [ ] **Step 4: Run tests to verify they pass**
  Run: `./mvnw -B -ntp -pl pulse-monolith test -Dtest='CommandServiceTest'`. Expected: PASS.
  Then, with Docker running: `./mvnw -B -ntp -pl pulse-monolith test -Dtest='CommandLifecycleQueriesTest,ApplicationSmokeTest'`. Expected: PASS. The smoke test proves Flyway V5 and Hibernate `validate` agree.

- [ ] **Step 5: Commit**
  ```bash
  git add pulse-monolith/src
  git commit -m "fix(command): time out queued and dispatched commands separately"
  ```

---

### Task 3: Mark silent vehicles offline

**Files:**
- Create: `pulse-monolith/src/main/java/com/pulse/connected/vehicle/ConnectivityProperties.java`
- Create: `pulse-monolith/src/main/java/com/pulse/connected/vehicle/VehicleConnectivityMonitor.java`
- Modify: `pulse-monolith/src/main/java/com/pulse/connected/vehicle/VehicleRepository.java`
- Create: `pulse-monolith/src/test/java/com/pulse/connected/vehicle/VehicleConnectivityMonitorTest.java`
- Modify: `pulse-monolith/src/test/java/com/pulse/connected/CommandLifecycleQueriesTest.java`

**Interfaces:**
- Consumes: `@ConfigurationPropertiesScan` from Task 2.
- Produces: `@ConfigurationProperties("pulse.connectivity") public record ConnectivityProperties(@DefaultValue("PT60S") Duration offlineAfter)`.
- Produces: `VehicleRepository.markOnlineVehiclesOfflineLastSeenBefore(Instant cutoff): int`, with `@Modifying(clearAutomatically = true, flushAutomatically = true)` and JPQL `UPDATE Vehicle v SET v.connectivityState = <OFFLINE> WHERE v.connectivityState = <ONLINE> AND (v.lastSeenAt IS NULL OR v.lastSeenAt < :cutoff)`. Use fully-qualified enum literals.
- Produces: `VehicleConnectivityMonitor.markSilentVehiclesOffline(): void`, `@Scheduled(fixedDelayString = "${pulse.connectivity.check-interval-ms:15000}")` and `@Transactional`. Logs `"Marked {} silent vehicle(s) OFFLINE"` at info when the count is > 0.

- [ ] **Step 1: Write the failing tests.**
  - `VehicleConnectivityMonitorTest.marksVehiclesSilentLongerThanOfflineAfter`: mock the repository and construct the monitor with `new ConnectivityProperties(Duration.ofSeconds(60))`. Capture the cutoff passed to the repository and assert it `isBetween(before.minus(60s), after.minus(60s))`.
  - In `CommandLifecycleQueriesTest`:
    - `silentOnlineVehicle_MarkedOffline`: save an `ONLINE` vehicle with `lastSeenAt = 1999-01-01`, call the repository with cutoff `2000-01-01T00:00:00Z`, reload it, and assert `OFFLINE`.
    - `recentlySeenVehicle_StaysOnline`: `ONLINE` with `lastSeenAt = now`, same old cutoff → still `ONLINE`.
    - `sleepingVehicle_Untouched`: `SLEEPING` with `lastSeenAt = 1999` → still `SLEEPING`.

    The fixed old cutoff only ever matches the test's own rows. `ONLINE` rows always have `lastSeenAt` set, because the heartbeat sets both.

- [ ] **Step 2: Run tests to verify they fail**
  Run: `./mvnw -B -ntp -pl pulse-monolith test -Dtest='VehicleConnectivityMonitorTest'`
  Expected: compilation failure.

- [ ] **Step 3: Implement** the record, the repository method and the monitor as specified in Interfaces.

- [ ] **Step 4: Run tests to verify they pass**
  Run: `./mvnw -B -ntp -pl pulse-monolith test -Dtest='VehicleConnectivityMonitorTest,CommandLifecycleQueriesTest,ModularityTests'`
  Expected: PASS. `ModularityTests` confirms there's no new cross-module access.

- [ ] **Step 5: Commit**
  ```bash
  git add pulse-monolith/src
  git commit -m "feat(vehicle): mark vehicles offline after missed heartbeats"
  ```

---

### Task 4: Dashboard and docs

**Files:**
- Modify: `pulse-dashboard/src/api/types.ts:6` (add `'CANCELLED'`, and `sentAt?: string` on the command type)
- Modify: `pulse-dashboard/src/utils/format.ts:32` (`CANCELLED: 'gray'`)
- Modify: `pulse-dashboard/src/components/LiveNotifications.tsx:9-16` (`CANCELLED` is final; notify with `'warning'`)
- Modify: `README.md:74` and `README.md:101` (state machine now includes `CANCELLED`; mention the 60s offline detection, the 15 min queue expiry and the 60s execution timeout, all configurable)

**Interfaces:**
- Consumes: `CommandResponse.sentAt` and `CommandStatus.CANCELLED` from Tasks 1–2.

- [ ] **Step 1: Make the changes** listed above. `commandColor` is a `Record<CommandStatus, …>`, so `tsc` fails until `CANCELLED` is added there.

- [ ] **Step 2: Verify**
  Run in `pulse-dashboard`: `npm test` and `npm run build`. Expected: tests pass; build completes with no type errors.

- [ ] **Step 3: Commit**
  ```bash
  git add pulse-dashboard/src README.md
  git commit -m "feat(dashboard): show cancelled commands; document command timeouts"
  ```

---

### Final verification

- [ ] With Docker running, from the repo root: `./mvnw -B -ntp verify`. Expected: `BUILD SUCCESS`.
- [ ] `git log --format='%an <%ae> | %cn <%ce>%n%B' <base>..HEAD`: author and committer are the user, with no Claude/Anthropic mention.
