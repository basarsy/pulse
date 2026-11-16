package com.pulse.connected.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shares live updates between instances over the Redis channel {@value #CHANNEL}.
 *
 * <p>Outgoing: callers run on request and Kafka listener threads, and an unreachable Redis makes
 * each command wait for its timeout. So envelopes are sent from one
 * background thread (keeping their order) through a bounded queue. When Redis can't keep up they
 * are dropped and counted, not retried: a late progress update is worth little.
 *
 * <p>Incoming: every instance subscribes, skips the envelopes it sent itself (it already delivered
 * those locally) and delivers the rest to its own STOMP broker.
 */
class RedisLiveUpdateRelay implements MessageListener, DisposableBean {

    static final String CHANNEL = "pulse:live-updates";
    private static final String FAILURES = "pulse.live.updates.forward.failed";
    private static final Duration WARN_INTERVAL = Duration.ofMinutes(1);
    private static final Logger log = LoggerFactory.getLogger(RedisLiveUpdateRelay.class);

    private final StringRedisTemplate redis;
    private final SimpMessageSendingOperations local;
    private final ObjectMapper json;
    private final MeterRegistry meters;
    private final String instanceId = UUID.randomUUID().toString();
    private final ThreadPoolExecutor sender;
    private final AtomicLong lastWarnedAt = new AtomicLong();

    RedisLiveUpdateRelay(StringRedisTemplate redis, SimpMessageSendingOperations local, ObjectMapper json,
                         MeterRegistry meters, int queueCapacity) {
        this.redis = redis;
        this.local = local;
        this.json = json;
        this.meters = meters;
        this.sender = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, "live-updates-relay");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    String instanceId() {
        return instanceId;
    }

    /** Queues the update for the other instances. Never throws and never blocks. */
    void forward(String destination, Object payload) {
        String message;
        try {
            message = json.writeValueAsString(new LiveUpdateEnvelope(instanceId, destination, json.valueToTree(payload)));
        } catch (Exception e) {
            log.warn("Could not serialise live update for [{}]: {}", destination, e.getMessage());
            return;
        }
        try {
            sender.execute(() -> send(destination, message));
        } catch (RejectedExecutionException e) {
            dropped("queue_full", "live update queue is full (or shutting down)");
        }
    }

    private void send(String destination, String message) {
        try {
            redis.convertAndSend(CHANNEL, message);
        } catch (Exception e) {
            dropped("redis_error", "could not relay live update for [" + destination + "]: " + e.getMessage());
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        LiveUpdateEnvelope envelope;
        try {
            envelope = json.readValue(new String(message.getBody(), StandardCharsets.UTF_8), LiveUpdateEnvelope.class);
        } catch (Exception e) {
            log.warn("Dropping malformed live update envelope: {}", e.getMessage());
            return;
        }
        if (instanceId.equals(envelope.origin())) {
            return;
        }
        // Forwarding happens after SUBSCRIBE authorisation, so only accept the live topics this
        // relay ever sends: anything able to PUBLISH to Redis must not reach other destinations.
        if (!isLiveTopic(envelope.destination())) {
            log.warn("Dropping live update envelope for unexpected destination [{}]", envelope.destination());
            return;
        }
        local.convertAndSend(envelope.destination(), envelope.payload());
    }

    private static boolean isLiveTopic(String destination) {
        return destination != null
                && (destination.startsWith("/topic/vehicles/") || destination.startsWith("/topic/rollouts/"));
    }

    private void dropped(String reason, String detail) {
        meters.counter(FAILURES, "reason", reason).increment();
        long now = System.nanoTime();
        long last = lastWarnedAt.get();
        if ((last == 0 || now - last >= WARN_INTERVAL.toNanos()) && lastWarnedAt.compareAndSet(last, now)) {
            log.warn("Dropped live update ({}): {}. Further drops in the next minute are only counted in [{}].",
                    reason, detail, FAILURES);
        }
    }

    @Override
    public void destroy() {
        sender.shutdownNow();
    }
}
