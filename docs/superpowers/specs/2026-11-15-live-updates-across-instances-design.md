# Live updates across instances

## Why

This is the second of two PRs that make `replicas: 2` work. The first (#11) coordinated the
scheduled jobs. What still blocks a second replica:

1. **Live updates reach only some browsers.** Each Kafka event is handled by one instance, which
   pushes the update through Spring's **in-memory** STOMP broker, so only browsers connected to
   *that* pod receive it. With two pods, a dashboard connected to the other pod misses rollout
   progress, OTA status and command updates. Its progress bar just freezes.
2. **The outbox can send duplicates across pods** (found in #11's review). With
   `republish-outstanding-events-on-restart: true`, Spring Modulith 1.2.5 republishes **every**
   incomplete event when an app starts, without a lock or an age filter. During a rolling deploy,
   the new pod re-sends events the old pod is still sending.

Pulse is a portfolio project. When this is done, `replicas: 2` should actually work, and a test
should prove it.

## Goals

- A live update produced on any instance reaches every subscribed browser on **every** instance,
  **exactly once** per browser.
- If Redis is down, browsers on the instance that produced the update still get it. The update
  never fails the business operation that triggered it.
- Restarting a pod no longer re-sends events another pod is handling. Failed events are still
  retried by the locked `outbox-resubmit` job from #11.
- `k8s/app/pulse-app-deployment.yaml` runs `replicas: 2`, and its comment says why that is safe.
  The README roadmap marks the scale-out work done.
- An integration test with **two running app instances** proves cross-instance delivery.

## Non-goals

- RabbitMQ or another external STOMP broker relay (option B2, rejected).
- Changing topics, payloads or the dashboard.
- Making every Kafka consumer idempotent. Kafka delivery stays at-least-once, as today.
- Docker Compose scale-out (`docker-compose.yml` keeps one app container).

## Approaches

(Chosen in conversation: B1.)

- **B1: share live updates over Redis pub/sub:** **chosen.** Redis is already deployed, and the
  dashboard and topics stay the same.
- *B2: STOMP broker relay to RabbitMQ:* rejected. It adds RabbitMQ to compose, k8s and CI for one
  feature.
- *B3: a Kafka topic that every instance reads:* rejected. Every instance needs its own consumer
  group, and stale groups have to be cleaned up.

## Design

### Live updates (`websocket` module)

- **New public API: `LiveUpdates.publish(String destination, Object payload)`.** `ota` and
  `command` call it instead of `SimpMessagingTemplate`. `OtaWebSocketService` and
  `CommandWebSocketService` keep their methods and only swap the dependency. Their call sites
  don't change.
- **Each update is delivered locally first, then forwarded.** `publish`:
  1. calls `SimpMessagingTemplate.convertAndSend(destination, payload)` for this instance's own
     browsers;
  2. publishes an envelope `{"origin": <instanceId>, "destination": ..., "payload": <payload as JSON>}`
     to the Redis channel **`pulse:live-updates`**.

- **The Redis send never runs on the caller's thread.** Every caller is inside a transaction,
  and some hold a campaign row lock (`RolloutCampaignService`) or run on Kafka consumer threads.
  While Redis is unreachable, Lettuce queues commands and waits up to `spring.data.redis.timeout`
  (2s), which would stall rollouts. So `publish`:
  - does the local delivery synchronously, as today;
  - **hands the envelope to a single-thread executor with a bounded queue** (1000 entries). One
    thread keeps updates in order.
  - When the queue is full, the new envelope is **dropped**.
  - A failed send is logged and dropped. Neither case is retried, because a late progress update
    is worth little.
  - Both cases increment the Micrometer counter `pulse.live.updates.forward.failed`, tagged
    `reason=queue_full|redis_error`. Warnings are logged at most once a minute, so a Redis outage
    doesn't flood the log.
  - The executor shuts down with the application context.
- **Receiving forwarded updates.** A `RedisMessageListenerContainer` subscribes every instance to
  `pulse:live-updates`. For each envelope:
  - if `origin` is this instance's id, **skip it**, because local delivery already happened;
  - otherwise call `convertAndSend(destination, payload)`, with the payload as a Jackson
    `JsonNode`, so browsers get the same JSON body as before.

  A malformed envelope is logged and dropped.
- **Instance id:** a random UUID generated at startup. Pod names would also work, but a UUID can't
  collide and needs no config.
- **Topic authorization is unchanged.** `StompTopicAuthorizer` checks SUBSCRIBE on each instance
  as before. Forwarding happens server-side, after authorization, so a browser still only receives
  topics it was allowed to subscribe to.
- **No sticky sessions needed.** The dashboard uses a raw WebSocket (`/ws-pulse/websocket`), so
  each connection stays on one pod. The SockJS fallback endpoint stays registered, but HTTP
  fallback transports would need sticky sessions. That's noted in the k8s comment.

### Outbox restart republish

- Set `spring.modulith.events.republish-outstanding-events-on-restart: false`.
- The `outbox-resubmit` job (#11) resubmits incomplete publications older than `retry-after`
  (1 min) on exactly one pod per interval, so events that failed before a crash are still
  retried, about a minute later instead of at startup.
- What remains is documented, not fixed: a send stuck longer than `retry-after` can be resubmitted
  while still in flight. Kafka delivery is at-least-once.

### Deployment and docs

- `k8s/app/pulse-app-deployment.yaml`: `replicas: 2`. The comment explains:
  - live updates are shared via Redis;
  - jobs are locked by ShedLock;
  - the SockJS fallback would need sticky sessions.
- README "Roadmap": record that the app scales horizontally.

### Tests

- **`LiveUpdatesAcrossInstancesTest`** (integration). The test's own context is instance **A**.
  It starts instance **B** as a second Spring Boot application on a random port, sharing the
  same Postgres, Redis and embedded Kafka, with the simulator disabled on B.
  - A STOMP client on **B** and a client on **A** subscribe to the same rollout topic, as an
    admin user.
  - An update is published on **A**, through `OtaWebSocketService`.
  - **Both** clients receive it, and each receives it **once**: no second copy arrives within a
    short quiet period.
- **`LiveUpdatesTest`** (unit, no Spring context):
  - `publish` delivers locally and publishes the envelope;
  - a Redis failure still delivers locally and doesn't throw;
  - **a Redis send that hangs doesn't delay `publish`**: it returns promptly while the send is
    blocked;
  - with the queue full, further envelopes are dropped and counted;
  - an envelope from this instance's origin is skipped;
  - an envelope from another origin is delivered to its destination;
  - a malformed envelope is dropped without throwing.
- **Existing `LiveUpdatesWebSocketTest`** keeps passing unchanged, so the single-instance
  behaviour is the same.

## Calls I made (you can override them)

- **Local delivery first, Redis second, skipping our own envelopes.** The alternative, every
  update going through Redis only, is simpler, but then a Redis outage stops all live updates.
- **Turning off restart republish** rather than making the consumers idempotent. It's a
  one-line change, and the locked resubmit job covers recovery.
- **A random UUID per instance** rather than the pod name.
- **Forwarding to Redis runs off the caller's thread** on one bounded queue, and drops updates
  when Redis can't keep up. There's no circuit breaker: the queue already isolates callers, and a
  breaker adds state for little gain.
- **One Redis channel for all topics** rather than one per destination. There are only three
  kinds of update, and one subscription is simpler.

## Left to the builder

- How instance B is started in the test (for example `SpringApplicationBuilder`, passing the
  containers' connection properties), and how it's shut down.
- The envelope's Java type (a record is fine) and its serialization.
- Class names inside `websocket`, apart from the public `LiveUpdates`.
