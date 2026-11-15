# Scheduled Job Locks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Across all instances, each of the six `@Scheduled` jobs runs at most once per interval, using ShedLock locks stored in Postgres.

**Architecture:** ShedLock's JDBC provider (database time) has a lock table created by Flyway V8 and
is enabled in the `config` module. Every scheduled method gets `@SchedulerLock`, with `lockAtLeastFor`
set to 80% of its configured interval. Task 1 proves the mechanism with a test job held mid-run.
Task 2 locks the real jobs and adds a test that fails if a future job has no lock.

**Tech Stack:** Spring Boot 3.3.5 (Spring 6.1), Java 21, ShedLock 6.10.0, Flyway, Testcontainers Postgres, JUnit 5, AssertJ, Awaitility.

**Spec:** `docs/superpowers/specs/2026-11-15-scheduled-job-locks-design.md`

## Global Constraints

- Use `net.javacrumbs.shedlock:shedlock-spring` and `shedlock-provider-jdbc-template` at **6.10.0**. 7.x needs Spring 7.
- Migration `V8__shedlock.sql` uses the standard table: `name varchar(64) primary key, lock_until timestamp not null, locked_at timestamp not null, locked_by varchar(255) not null`.
- `@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")`, and the `JdbcTemplateLockProvider` uses `.usingDbTime()`.
- Lock names, exactly:
  - `outbox-resubmit`
  - `outbox-purge`
  - `command-stale-sweep`
  - `connectivity-offline-check`
  - `simulator-heartbeats`
  - `simulator-telemetry`
- `lockAtLeastFor` is `#{<interval-ms expression> * 8 / 10}`, built from the same property and default as the `@Scheduled` annotation. `command-stale-sweep` has a hard-coded 30000, so it uses `"24000"`.
- `outbox-purge` also sets `lockAtMostFor = "${pulse.outbox.purge-interval-ms:3600000}"`.
- Don't change what any job does.
- Integration tests need Docker Desktop running. Run them with `./mvnw -B -ntp -pl pulse-monolith verify` from the repo root, or `-Dtest=<Class>` for one class.
- Commits are authored by basarsy, with no Claude or Anthropic mentions.

## Review Focus

1. **The lock commits outside the job's transaction.** The stale-command sweep and offline detection are `@Transactional`. If the lock insert joined their transaction, other instances wouldn't see the lock until the job committed. Task 1's test checks the lock row from a separate connection while a `@Transactional` test job is held mid-run.
2. **The SpEL durations are parsed as milliseconds.** If ShedLock read `400` as anything else, or failed to read it, jobs would run too often or the app wouldn't start. Task 2's wiring test checks that the `simulator-heartbeats` row holds its lock for 400ms when the interval is 500ms.
3. **Package-private job methods are actually intercepted.** `OutboxMaintenance`'s methods are package-private. A proxy that skips them leaves the outbox unlocked and no error anywhere. Task 2's wiring test waits for all six lock rows to appear.
4. **`lockAtLeastFor` never exceeds `lockAtMostFor`.** ShedLock rejects that combination. The purge job's 48 minutes would exceed the 5-minute default, which is why it sets its own `lockAtMostFor`. The wiring test proves startup and each job's first run work, including the purge.
5. **ShedLock 6.10.0 works with Spring 6.1.** It's built against 6.2. Task 1 runs the full test suite.

---

### Task 1: Enable ShedLock, with a concurrency test

**Files:**
- Modify: `pulse-monolith/pom.xml` (two dependencies)
- Create: `pulse-monolith/src/main/resources/db/migration/V8__shedlock.sql`
- Create: `pulse-monolith/src/main/java/com/pulse/connected/config/SchedulingLockConfig.java`
- Test: `pulse-monolith/src/test/java/com/pulse/connected/SchedulerLockTest.java`

**Interfaces:**
- Produces: a `LockProvider` bean, with `@EnableSchedulerLock` active for the whole app. Task 2 relies on it.

- [ ] **Step 1: Add the two dependencies** at version 6.10.0, declared inline or as a `<shedlock.version>` property.

- [ ] **Step 2: Write the failing test.** `SchedulerLockTest extends AbstractIntegrationTest` and `@Import`s a nested `@TestConfiguration` that defines a bean `HeldJob`. `HeldJob` has one method, `@Transactional @SchedulerLock(name = "test-held-job", lockAtMostFor = "PT1M") public void run()`. The method increments an `AtomicInteger runs`, counts down `started`, then awaits `release` (up to 10s).

```java
@Test
void secondConcurrentRun_IsSkippedWhileTheFirstHoldsTheLock() throws Exception {
    Future<?> first = executor.submit(job::run);
    assertThat(job.started.await(5, SECONDS)).isTrue();

    // A separate connection already sees the lock while the job's transaction is still open.
    Timestamp lockUntil = jdbc.queryForObject(
            "select lock_until from shedlock where name = 'test-held-job'", Timestamp.class);
    assertThat(lockUntil.toInstant()).isAfter(dbNow());

    executor.submit(job::run).get(5, SECONDS);   // returns at once: the lock is taken
    job.release.countDown();
    first.get(5, SECONDS);

    assertThat(job.runs).hasValue(1);
}
```

  - `jdbc` is an autowired `JdbcTemplate`.
  - `dbNow()` is `select now()`.
  - `executor` is a 2-thread pool shut down in `@AfterEach`.

- [ ] **Step 3: Run it. Expected: FAIL.** Without `@EnableSchedulerLock` nothing intercepts `run()`. Either the `shedlock` relation doesn't exist, or the second call blocks until it times out.

  Run: `./mvnw -B -ntp -pl pulse-monolith test -Dtest=SchedulerLockTest`

- [ ] **Step 4: Implement the lock setup.**
  - Add `V8__shedlock.sql`, using the table from Global Constraints.
  - Add `SchedulingLockConfig` (`@Configuration`, `@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")`) with a `@Bean LockProvider lockProvider(DataSource dataSource)`. It returns a `JdbcTemplateLockProvider` built with `JdbcTemplateLockProvider.Configuration.builder().withJdbcTemplate(new JdbcTemplate(dataSource)).usingDbTime().build()`.
  - The class Javadoc says why: one run per interval across instances, with the database clock.

- [ ] **Step 5: Run it. Expected: PASS.** If the separate-connection assertion fails, the lock is joining the job's transaction. Fix that in the provider config, for example with `.withTransactionManager(...)` or the provider's REQUIRES_NEW behaviour. Don't weaken the test.

- [ ] **Step 6: Run the full suite.** `./mvnw -B -ntp -pl pulse-monolith verify`. Expected: everything passes, including `ModularityTests`.

- [ ] **Step 7: Commit**

```bash
git add pulse-monolith/pom.xml pulse-monolith/src/main/resources/db/migration/V8__shedlock.sql pulse-monolith/src/main/java/com/pulse/connected/config/SchedulingLockConfig.java pulse-monolith/src/test/java/com/pulse/connected/SchedulerLockTest.java
git commit -m "feat(scheduling): ShedLock on Postgres so a locked job runs on one instance at a time"
```

### Task 2: Lock every scheduled job

**Files:**
- Modify: `pulse-monolith/src/main/java/com/pulse/connected/messaging/OutboxMaintenance.java`, `command/CommandService.java`, `vehicle/VehicleConnectivityMonitor.java`, `simulator/VehicleSimulatorRunner.java`
- Modify: `k8s/app/pulse-app-deployment.yaml:9-12` (the comment)
- Test: `pulse-monolith/src/test/java/com/pulse/connected/ScheduledJobLockCoverageTest.java` (a plain unit test, no Spring context)
- Test: `pulse-monolith/src/test/java/com/pulse/connected/ScheduledJobLockWiringTest.java` (`extends AbstractIntegrationTest`, with no extra config, so it reuses the cached context)

**Interfaces:**
- Consumes: Task 1's `@EnableSchedulerLock` and `LockProvider`.

- [ ] **Step 1: Write the coverage test.**
  - Scan `com.pulse.connected` with `ClassPathScanningCandidateComponentProvider(false)` and an `AnnotationTypeFilter(Component.class)`.
  - Load each class, and collect every declared method annotated with `@Scheduled`.

```java
@Test
void everyScheduledJobHasAUniquelyNamedLock() {
    List<Method> jobs = scheduledMethods();
    assertThat(jobs).allSatisfy(m -> assertThat(m.getAnnotation(SchedulerLock.class))
            .as("%s.%s has no @SchedulerLock", m.getDeclaringClass().getSimpleName(), m.getName())
            .isNotNull());
    assertThat(jobs).extracting(m -> m.getAnnotation(SchedulerLock.class).name())
            .containsExactlyInAnyOrder("outbox-resubmit", "outbox-purge", "command-stale-sweep",
                    "connectivity-offline-check", "simulator-heartbeats", "simulator-telemetry");
}
```

- [ ] **Step 2: Write the wiring test.**

```java
@Test
void everyJobRunsThroughItsLock() {
    await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
            assertThat(jdbc.queryForList("select name from shedlock", String.class))
                    .contains("outbox-resubmit", "outbox-purge", "command-stale-sweep",
                            "connectivity-offline-check", "simulator-heartbeats", "simulator-telemetry"));
}

@Test
void lockAtLeastForIs80PercentOfTheConfiguredInterval() {
    // AbstractIntegrationTest sets the heartbeat interval to 500ms, so a released lock lasts 400ms.
    await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            assertThat(jdbc.queryForObject("""
                    select extract(epoch from (lock_until - locked_at)) * 1000
                    from shedlock where name = 'simulator-heartbeats'""", Double.class))
                    .isBetween(400.0, 450.0));
}
```

  All six jobs run as soon as the context starts, because none has an initial delay.

- [ ] **Step 3: Run both. Expected: FAIL.** The coverage test lists 6 methods without `@SchedulerLock`. The wiring test times out with no job rows.

  Run: `./mvnw -B -ntp -pl pulse-monolith test -Dtest='ScheduledJobLock*'`

- [ ] **Step 4: Annotate the six methods.** Use the names and `lockAtLeastFor` rules from Global Constraints. For example, `resubmitFailedPublications` gets `@SchedulerLock(name = "outbox-resubmit", lockAtLeastFor = "#{${pulse.outbox.resubmit-interval-ms:60000} * 8 / 10}")`. `outbox-purge` also sets its `lockAtMostFor`.

- [ ] **Step 5: Run both. Expected: PASS.** Then run the full suite with `./mvnw -B -ntp -pl pulse-monolith verify`. Expected: everything passes. The simulator-driven flows still finish in time, which proves the 400ms lock doesn't throttle the 500ms heartbeat.

- [ ] **Step 6: Update the k8s comment.** Keep `replicas: 1`. The `@Scheduled` bullet now says the jobs are coordinated with ShedLock. The remaining blocker is the in-memory STOMP broker.

- [ ] **Step 7: Commit**

```bash
git add pulse-monolith/src k8s/app/pulse-app-deployment.yaml
git commit -m "feat(scheduling): lock every scheduled job so it runs once per interval across instances"
```
