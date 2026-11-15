# Live Updates Across Instances Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A live update produced on any instance reaches each subscribed browser on every instance exactly once. Restarts stop re-sending outbox events, and k8s runs 2 replicas.

**Architecture:** `websocket.LiveUpdates.publish` delivers to this pod's STOMP broker, then hands the update to `RedisLiveUpdateRelay`. The relay sends an envelope to Redis channel `pulse:live-updates` from a single background thread with a bounded queue. Every pod's relay subscribes to that channel, skips its own envelopes, and delivers the rest to its local broker. Task 1 builds and unit-tests the relay. Task 2 wires the push services to it, proves cross-pod delivery with two running app instances, and flips the deployment config.

**Tech Stack:** Spring Boot 3.3.5, Spring Data Redis 3.3.5 (Lettuce), Spring WebSocket/STOMP, Jackson, Micrometer, JUnit 5, Mockito, AssertJ, Awaitility, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-11-15-live-updates-across-instances-design.md`

## Global Constraints

- Redis channel: exactly `pulse:live-updates`.
- Envelope JSON: `{"origin": "<instance uuid>", "destination": "<stomp destination>", "payload": <payload JSON>}`.
- Queue capacity: **1000**, on a single sender thread. When the queue is full, the new envelope is dropped.
- Neither a full queue nor a failed send is retried.
- Counter: `pulse.live.updates.forward.failed`, tag `reason` = `queue_full` | `redis_error`.
- Drop warnings are logged at most once per minute.
- `publish` must never throw, and must never wait on Redis.
- Topics, payloads and the dashboard don't change.
- `spring.modulith.events.republish-outstanding-events-on-restart: false`.
- `k8s/app/pulse-app-deployment.yaml`: `replicas: 2`.
- Backend tests: `./mvnw -B -ntp -pl pulse-monolith test -Dtest=<Class>` from the repo root, with Docker running. Full suite: `./mvnw -B -ntp -pl pulse-monolith verify`. k8s render: `cp k8s/config/secrets.env.example k8s/config/secrets.env && kubectl kustomize k8s > /dev/null`, then delete the copied `secrets.env` if it wasn't there before.
- Commits are authored by basarsy, with no Claude or Anthropic mentions.

## Review Focus

1. **The app still starts when Redis is down.** Today a missing Redis only breaks the command rate limiter. A listener container that blocks or fails at startup would make Redis a hard dependency for booting. Task 2 starts an instance whose Redis port is closed and asserts it boots and serves `/actuator/health/liveness`.
2. **A forwarded payload is the same JSON as a local one.** That includes `Instant` fields and nulls. Task 2 asserts that the update received on B equals the one received on A, field for field.
3. **Updates keep their order on the receiving pod.** One sender thread plus Redis pub/sub keeps the per-publisher order. Task 1 asserts that two envelopes published in sequence go out to Redis in that order.
4. **The second instance in the test doesn't disturb later tests.** It must not join the Kafka consumer group or keep running. Task 2 starts B with `spring.kafka.listener.auto-startup=false` and closes it in `@AfterAll`.
5. **The sender thread stops with the context.** Otherwise each closed test context leaks a thread. Task 1 asserts that the executor is shut down after `destroy()`.

---

### Task 1: The Redis relay and `LiveUpdates` (websocket module)

**Files:**
- Create: `pulse-monolith/src/main/java/com/pulse/connected/websocket/LiveUpdates.java`
- Create: `pulse-monolith/src/main/java/com/pulse/connected/websocket/RedisLiveUpdateRelay.java`
- Create: `pulse-monolith/src/main/java/com/pulse/connected/websocket/LiveUpdateEnvelope.java`
- Create: `pulse-monolith/src/main/java/com/pulse/connected/websocket/LiveUpdatesRedisConfig.java`
- Test: `pulse-monolith/src/test/java/com/pulse/connected/websocket/LiveUpdatesTest.java` (a unit test with no Spring context)

**Interfaces:**
- Produces: `public class LiveUpdates`, a `@Component` with `public void publish(String destination, Object payload)`. It calls `SimpMessageSendingOperations.convertAndSend(destination, payload)`, then `relay.forward(destination, payload)`. Task 2 consumes it.
- `RedisLiveUpdateRelay` is package-private, `implements MessageListener, DisposableBean`:
  - constructor `(StringRedisTemplate redis, SimpMessageSendingOperations local, ObjectMapper json, MeterRegistry meters, int queueCapacity)`;
  - `void forward(String destination, Object payload)`, which serialises and enqueues and never throws;
  - `onMessage(Message message, byte[] pattern)`, which parses the envelope, skips it when `origin` equals `instanceId()`, and otherwise calls `local.convertAndSend(destination, payloadJsonNode)`. A malformed envelope is logged and dropped.
  - `String instanceId()`, a random UUID from construction.
  - `destroy()`, which shuts the executor down.

  The sender is a `ThreadPoolExecutor(1, 1, …, new ArrayBlockingQueue<>(queueCapacity))` that counts rejected tasks as `queue_full`.
- `record LiveUpdateEnvelope(String origin, String destination, JsonNode payload)`.
- `LiveUpdatesRedisConfig` (`@Configuration`):
  - a `@Bean RedisLiveUpdateRelay`, with capacity 1000;
  - a `@Bean RedisMessageListenerContainer` on the auto-configured `RedisConnectionFactory`, which subscribes the relay to `new ChannelTopic("pulse:live-updates")`.

- [ ] **Step 1: Write the failing tests.**
  - Fixtures:
    - `redis` is a Mockito mock of `StringRedisTemplate`;
    - `local` is a mock of `SimpMessageSendingOperations`;
    - `json` is `new ObjectMapper().findAndRegisterModules()`;
    - `meters` is a `SimpleMeterRegistry`.
  - Use Awaitility for anything asynchronous.
  - Tests:
    - `publish_DeliversLocallyAndForwardsTheEnvelope`. `LiveUpdates.publish("/topic/x", Map.of("status","SENT"))` gives:
      - `local.convertAndSend("/topic/x", thatMap)`;
      - eventually, `redis.convertAndSend(eq("pulse:live-updates"), captor)`, where the parsed envelope has `origin == relay.instanceId()`, `destination == "/topic/x"` and `payload.status == "SENT"`.
    - `forwardsInPublishOrder`: two publishes, and the captured Redis messages are in the same order.
    - `redisError_StillDeliversLocally_AndIsCounted`: `redis.convertAndSend` throws `RedisConnectionFailureException`. `publish` doesn't throw, local delivery happens, and the counter `reason=redis_error` eventually reaches 1.
    - `hangingRedis_DoesNotDelayPublish`: `redis.convertAndSend` blocks on a latch, and `publish` returns in under 200ms. Release the latch afterwards.
    - `fullQueue_DropsAndCounts`:
      - capacity 1, and Redis blocks on a latch;
      - publish 3 times: one send is running, one is queued, and the third is dropped;
      - `reason=queue_full` is 1 and `publish` never threw.
    - `ownEnvelope_IsSkipped`: `onMessage` with `origin = relay.instanceId()` never calls `local`.
    - `foreignEnvelope_IsDeliveredToItsDestination`: `onMessage` with another origin calls `local.convertAndSend("/topic/x", payloadNode)`, and `payloadNode.get("status").asText()` is `"SENT"`.
    - `malformedEnvelope_IsDropped`: `onMessage` with the body `"not json"` doesn't throw and never calls `local`.
    - `destroy_ShutsTheSenderDown`: after `destroy()`, a further `forward` is counted as `queue_full`, because the executor rejects it, and doesn't throw.

- [ ] **Step 2: Run them. Expected: FAIL to compile** (the classes don't exist yet).

  Run: `./mvnw -B -ntp -pl pulse-monolith test -Dtest=LiveUpdatesTest`

- [ ] **Step 3: Implement** the four classes as described in Interfaces. Each class Javadoc states the "why" in one or two lines.
  - The once-per-minute warning is an `AtomicLong` holding the last-warned time.
  - The thread name is `live-updates-relay`, as a daemon thread.

- [ ] **Step 4: Run them. Expected: PASS.** Then run `-Dtest='LiveUpdatesTest,ModularityTests,LiveUpdatesWebSocketTest'`. Expected: PASS. Nothing calls `LiveUpdates` yet, and the listener container starts against the test Redis.

- [ ] **Step 5: Commit**

```bash
git add pulse-monolith/src/main/java/com/pulse/connected/websocket pulse-monolith/src/test/java/com/pulse/connected/websocket/LiveUpdatesTest.java
git commit -m "feat(websocket): relay live updates to other instances over Redis without blocking callers"
```

### Task 2: Send every push through `LiveUpdates`, prove it across two instances, and scale to 2

**Files:**
- Modify: `pulse-monolith/src/main/java/com/pulse/connected/ota/OtaWebSocketService.java` and `command/CommandWebSocketService.java`. Each swaps its `SimpMessagingTemplate` dependency for `LiveUpdates`; destinations and logs stay the same.
- Modify: `pulse-monolith/src/main/resources/application.yml:34`, setting `republish-outstanding-events-on-restart: false` with a one-line comment. The locked `outbox-resubmit` job retries instead.
- Modify: `k8s/app/pulse-app-deployment.yaml`:
  - set `replicas: 2`;
  - rewrite the comment to say that live updates are shared via Redis, that jobs are locked by ShedLock, and that the SockJS HTTP fallback would need sticky sessions while the dashboard uses a raw WebSocket.
- Modify: `README.md`, in the "Development Roadmap & Progress" section: record horizontal scaling as done.
- Test: `pulse-monolith/src/test/java/com/pulse/connected/LiveUpdatesAcrossInstancesTest.java` (`extends AbstractIntegrationTest`)

**Interfaces:**
- Consumes: `LiveUpdates.publish(String, Object)` from Task 1.

- [ ] **Step 1: Write the failing test.**
  - **Starting instance B:** in a `startInstance(Map<String,String> overrides)` helper, use `new SpringApplicationBuilder(PulseApplication.class).properties(...)`. B uses `server.port=0` and the same JWT secret as `AbstractIntegrationTest`. Its connections:
    - Postgres: `spring.datasource.url`, `username` and `password` from `POSTGRES`;
    - Redis: `spring.data.redis.host` and `port` from `REDIS`;
    - Kafka: `spring.kafka.bootstrap-servers`, read from this context's `Environment`.

    It also sets `spring.kafka.listener.auto-startup=false`, `pulse.simulator.enabled=false` and `spring.jmx.enabled=false`.
  - **Using instance B:**
    - read B's port from `local.server.port`;
    - close every started instance in `@AfterAll`;
    - before publishing, wait until `b.getBean(RedisMessageListenerContainer.class).isListening()`.
  - **The STOMP client:** copy the `connect`/`collectInto` helpers from `LiveUpdatesWebSocketTest`, parameterised by port. Subscribe with `session.setAutoReceipt(true)`, and wait for each subscription's receipt before publishing.

```java
@Test
void updatePublishedOnA_ReachesSubscribersOnBothInstances_ExactlyOnce() throws Exception {
    String token = registerAndGetToken("ws-multi");
    String vehicleId = createVehicle(token);
    String topic = "/topic/vehicles/" + vehicleId + "/commands";
    ConfigurableApplicationContext b = startInstance(Map.of());

    BlockingQueue<Map<String, Object>> onA = subscribe(port, token, topic);
    BlockingQueue<Map<String, Object>> onB = subscribe(localPort(b), token, topic);

    CommandResponse update = CommandResponse.builder().id(UUID.randomUUID()).vehicleId(UUID.fromString(vehicleId))
            .type(CommandType.LOCK).status(CommandStatus.SENT).requestedAt(Instant.now()).build();
    commandWebSocketService.notifyCommandStatusChange(UUID.fromString(vehicleId), update);   // on A

    Map<String, Object> fromA = onA.poll(10, SECONDS);
    Map<String, Object> fromB = onB.poll(10, SECONDS);
    assertThat(fromA).isNotNull();
    assertThat(fromB).isEqualTo(fromA);                       // same JSON, Instants and nulls included
    assertThat(onA.poll(1, SECONDS)).isNull();                // exactly once on each instance
    assertThat(onB.poll(1, SECONDS)).isNull();
}

@Test
void instanceStartsWithoutRedis() {
    // Probes are only on by default inside Kubernetes; /health itself is DOWN without Redis.
    ConfigurableApplicationContext c = startInstance(Map.of("spring.data.redis.port", "1",
            "management.endpoint.health.probes.enabled", "true"));
    // GET http://localhost:<port>/actuator/health/liveness → 200
}
```

  `commandWebSocketService` is the autowired bean in context A. Use `CommandType`/`CommandStatus` values that exist in `com.pulse.connected.domain`. For the liveness probe, use Spring's `RestClient`, or `TestRestTemplate` against the port.

- [ ] **Step 2: Run it. Expected: the first test FAILS.** `fromB` is null, because B never receives A's update. `instanceStartsWithoutRedis` should already pass. If it doesn't, that's Review Focus 1: fix startup in Step 3, for example with the container's `setRecoveryBackoff` or by not failing the context on subscription errors.

  Run: `./mvnw -B -ntp -pl pulse-monolith test -Dtest=LiveUpdatesAcrossInstancesTest`

- [ ] **Step 3: Wire the push services, and change the config, k8s and README** as listed under Files.

- [ ] **Step 4: Run it. Expected: PASS.** Then the full suite with `./mvnw -B -ntp -pl pulse-monolith verify` (expected: everything passes), then the k8s render command from Global Constraints (expected: exit 0).

- [ ] **Step 5: Commit**

```bash
git add pulse-monolith/src pulse-monolith/src/main/resources/application.yml k8s/app/pulse-app-deployment.yaml README.md
git commit -m "feat(websocket): every instance pushes every live update; run 2 replicas"
```
