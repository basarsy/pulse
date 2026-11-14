# Command Idempotency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retried commands with the same idempotency key always return the original command: no duplicates, no 404/500, no rate-limit cost.

**Architecture:** The `(issued_by, idempotency_key)` unique constraint becomes the only source of truth. `issueCommand` looks the key up, rate-limits only new commands, and creates the command in its own transaction. If the constraint rejects the insert (a concurrent twin won), it re-reads the winner in a fresh transaction. Redis keeps only the rate limit.

**Tech Stack:** Java 21, Spring Boot 3.3, Spring Data JPA, Spring `TransactionOperations`, Jakarta Validation, JUnit 5 + Mockito + AssertJ, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-11-14-command-idempotency-design.md`

## Global Constraints

- Keys are valid forever, per user. Replays return the original command with HTTP 202 (unchanged) and no new audit entry, metric, dispatch or WebSocket push.
- Same key with a different vehicle or a different type → `DuplicateResourceException` (409).
- Key longer than 100 characters → 400. A blank key means "no key", and is stored as `null`.
- The rate limit (Redis, 10/min) applies to new commands only.
- No new dependencies. Commits as `basarsy <basarsoyturk03@gmail.com>`, no Claude/Anthropic attribution.
- Backend tests: `./mvnw -B -ntp -pl pulse-monolith test -Dtest=<Class>` from the repo root. Integration tests need Docker.

## Review Focus

1. A replay after the original command has already finished (e.g. `COMPLETED`) must return it, not 409 or a new command. → Task 1 test.
2. A rate-limited user replaying an existing key gets the command, not 429. → Task 1 test.
3. Two blank-key (`"  "`) requests from one user must both create commands; today the blank string is stored and the second hits the constraint. → Task 1 test.
4. A key of exactly 100 characters is accepted; 101 is rejected with 400. → Task 1 test.
5. A constraint violation on a request *without* a key must not be swallowed as a replay. → Task 2 test.

---

### Task 1: Database-backed lookup, matching rules, rate-limit order

**Files:**
- Rename: `pulse-monolith/src/main/java/com/pulse/connected/command/CommandIdempotencyService.java` → `CommandRateLimiter.java` (keep only `checkRateLimit` and its constants; delete the idempotency methods and constants)
- Rename: `pulse-monolith/src/test/java/com/pulse/connected/command/CommandIdempotencyServiceTest.java` → `CommandRateLimiterTest.java` (drop `idempotencyKeys_AreScopedPerUser`)
- Modify: `pulse-monolith/src/main/java/com/pulse/connected/command/RemoteCommandRepository.java`
- Modify: `pulse-monolith/src/main/java/com/pulse/connected/command/CommandService.java` (`issueCommand`, constructor)
- Modify: `pulse-monolith/src/main/java/com/pulse/connected/command/dto/IssueCommandRequest.java`
- Test: `pulse-monolith/src/test/java/com/pulse/connected/command/CommandServiceTest.java`
- Create: `pulse-monolith/src/test/java/com/pulse/connected/command/dto/IssueCommandRequestTest.java`

**Interfaces:**
- Produces: `class CommandRateLimiter` (package-private is fine) with `boolean checkRateLimit(UUID userId)`.
- Produces: `RemoteCommandRepository.findByIssuedByIdAndIdempotencyKey(UUID userId, String idempotencyKey): Optional<RemoteCommand>`. Remove `findByIdempotencyKey`.
- Produces: `CommandService` constructor with `CommandRateLimiter rateLimiter` in place of `CommandIdempotencyService idempotencyService` (same position).
- Produces: private `CommandResponse replay(RemoteCommand existing, UUID vehicleId, IssueCommandRequest request)`. It throws `DuplicateResourceException` when the vehicle or type differs, and otherwise returns `mapToResponse(existing)`. Task 2 reuses it.

- [ ] **Step 1: Write the failing tests.** In `CommandServiceTest`, replace the `idempotencyService` mock with `@Mock CommandRateLimiter rateLimiter` and update the existing stubs. Add:
  - `issueCommand_KnownKey_ReturnsOriginalWithoutSideEffects`: `findByIssuedByIdAndIdempotencyKey(owner.getId(), "k")` returns a `COMPLETED` LOCK command for `onlineVehicle`. Request LOCK/"k" → response id equals the original's. `verify(commandRepository, never()).save(any())`, `verifyNoInteractions(eventPublisher, webSocketService, auditLogService, rateLimiter)`.
  - `issueCommand_KnownKeyWhileRateLimited_StillReturnsOriginal`: same, without stubbing `rateLimiter`. Expect no exception and no rate-limiter call (`verifyNoInteractions(rateLimiter)`).
  - `issueCommand_KnownKeyDifferentType_Conflict`: original LOCK, request UNLOCK/"k" → `DuplicateResourceException`.
  - Keep `issueCommand_IdempotencyKeyReusedForAnotherVehicle_Conflict`, now stubbing the repository finder.
  - `issueCommand_BlankKey_StoredAsNoKey`: request with key `"  "`, rate limit OK, vehicle found. Capture the saved `RemoteCommand` and assert `getIdempotencyKey()` is null. Also `verify(commandRepository, never()).findByIssuedByIdAndIdempotencyKey(any(), any())`.
  - In `IssueCommandRequestTest` (plain `Validation.buildDefaultValidatorFactory().getValidator()`): `keyOf100Chars_IsValid` (no violations) and `keyOf101Chars_IsRejected` (one violation on `idempotencyKey`).

- [ ] **Step 2: Run tests to verify they fail**
  Run: `./mvnw -B -ntp -pl pulse-monolith test -Dtest='CommandServiceTest,IssueCommandRequestTest,CommandRateLimiterTest'`
  Expected: compilation failure (`CommandRateLimiter` and the new finder don't exist).

- [ ] **Step 3: Implement.**
  - Do the rename. Add the finder. Add `@Size(max = 100, message = "Idempotency key must be at most 100 characters")` on `idempotencyKey`.
  - `issueCommand` order: normalise the key (blank → `null`). If the key is non-null and `findByIssuedByIdAndIdempotencyKey` finds a command, `return replay(...)`. Otherwise run `rateLimiter.checkRateLimit` (→ `RateLimitExceededException`), then the existing create path, storing the normalised key. Remove both Redis idempotency calls.
  - Keep `@Transactional` on `issueCommand` for now; Task 2 replaces it.

- [ ] **Step 4: Run tests to verify they pass**
  Run: the Step 2 command. Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**
  ```bash
  git add -A pulse-monolith/src
  git commit -m "fix(command): decide idempotency in the database, not Redis"
  ```

---

### Task 2: Concurrent twins get the same command

**Files:**
- Modify: `pulse-monolith/src/main/java/com/pulse/connected/command/CommandService.java`
- Test: `pulse-monolith/src/test/java/com/pulse/connected/command/CommandServiceTest.java`
- Create: `pulse-monolith/src/test/java/com/pulse/connected/IdempotencyRaceTest.java` (extends `AbstractIntegrationTest`, **not** `@Transactional`)
- Modify: `README.md:78`, `README.md:104`

**Interfaces:**
- Consumes: `replay(...)` and `findByIssuedByIdAndIdempotencyKey` from Task 1.
- Produces: the `CommandService` constructor gains a last parameter `TransactionOperations tx`. Spring Boot provides a `TransactionTemplate` bean; unit tests pass `TransactionOperations.withoutTransaction()`.

- [ ] **Step 1: Write the failing tests.**
  - In `CommandServiceTest` (construct with `TransactionOperations.withoutTransaction()`):
    - `issueCommand_LosesInsertRace_ReturnsWinner`: the finder returns `Optional.empty()` first and then the winner (same vehicle and type), rate limit OK, vehicle found. `saveAndFlush(any())` throws `new DataIntegrityViolationException("uq_remote_commands_issuer_idempotency_key")`. Response id equals the winner's, and `verifyNoInteractions(eventPublisher, auditLogService)`.
    - `issueCommand_ConstraintViolationWithoutKey_Rethrows`: no key; `saveAndFlush` throws `DataIntegrityViolationException` → the same exception propagates.
  - `IdempotencyRaceTest.concurrentTwins_CreateOneCommand`: save a user and a vehicle directly (as in `CommandLifecycleQueriesTest`). Start 8 threads on a `CountDownLatch` that each call `commandService.issueCommand(vehicleId, LOCK/"race-<uuid>", user)`, and collect results and exceptions. Assert no exceptions, all 8 response ids are equal, and exactly one row exists for (user, key).

    This test is probabilistic before the fix (it fails whenever two threads race) and deterministic after it. The deterministic RED is the unit test above.

- [ ] **Step 2: Run tests to verify they fail**
  Run: `./mvnw -B -ntp -pl pulse-monolith test -Dtest='CommandServiceTest'`
  Expected: compilation failure (constructor arity), then, after adding the parameter only, `issueCommand_LosesInsertRace_ReturnsWinner` fails with `DataIntegrityViolationException`.

- [ ] **Step 3: Implement.**
  - Remove `@Transactional` from `issueCommand`.
  - Run the lookup in `tx.execute(...)`, then the rate limit outside any transaction, then the create path in `tx.execute(...)`, using `saveAndFlush`.
  - Catch `DataIntegrityViolationException` around the create call. With a key, re-read in a new `tx.execute(...)`: return `replay(...)` if found, otherwise rethrow. Without a key, rethrow.
  - `mapToResponse` touches lazy `vehicle`/`issuedBy`, so build the response inside each transaction.
  - README: replace "Redis-backed, per-user idempotency keys" with database-backed per-user keys that never expire (Redis does the rate limit). Remove "(24h TTL)" at line 104.

- [ ] **Step 4: Run tests to verify they pass**
  Run: `./mvnw -B -ntp -pl pulse-monolith test -Dtest='CommandServiceTest,IdempotencyRaceTest,ApplicationSmokeTest'`
  Expected: PASS. `ApplicationSmokeTest` issues a keyed command end to end.

- [ ] **Step 5: Commit**
  ```bash
  git add pulse-monolith/src README.md
  git commit -m "fix(command): concurrent requests with the same key return one command"
  ```

---

### Final verification

- [ ] With Docker running: `./mvnw -B -ntp verify` → `BUILD SUCCESS`.
- [ ] `git log --format='%an <%ae> | %cn <%ce>%n%B' main..HEAD`: author and committer are the user, with no Claude/Anthropic mention.
