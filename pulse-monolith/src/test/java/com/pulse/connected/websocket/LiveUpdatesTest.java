package com.pulse.connected.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveUpdatesTest {

    private static final String CHANNEL = "pulse:live-updates";

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final SimpMessageSendingOperations local = mock(SimpMessageSendingOperations.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final CountDownLatch redisBlocked = new CountDownLatch(1);

    private RedisLiveUpdateRelay relay = new RedisLiveUpdateRelay(redis, local, json, meters, 1000);
    private LiveUpdates liveUpdates = new LiveUpdates(local, relay);

    @AfterEach
    void tearDown() throws Exception {
        redisBlocked.countDown();
        relay.destroy();
    }

    @Test
    void publish_DeliversLocallyAndForwardsTheEnvelope() throws Exception {
        Map<String, String> payload = Map.of("status", "SENT");

        liveUpdates.publish("/topic/x", payload);

        verify(local).convertAndSend("/topic/x", payload);
        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(redis, timeout(2000)).convertAndSend(eq(CHANNEL), sent.capture());
        JsonNode envelope = json.readTree(sent.getValue());
        assertThat(envelope.get("origin").asText()).isEqualTo(relay.instanceId());
        assertThat(envelope.get("destination").asText()).isEqualTo("/topic/x");
        assertThat(envelope.get("payload").get("status").asText()).isEqualTo("SENT");
    }

    @Test
    void forwardsInPublishOrder() throws Exception {
        liveUpdates.publish("/topic/x", Map.of("n", 1));
        liveUpdates.publish("/topic/x", Map.of("n", 2));

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(redis, timeout(2000).times(2)).convertAndSend(eq(CHANNEL), sent.capture());
        List<String> messages = sent.getAllValues();
        assertThat(json.readTree(messages.get(0)).get("payload").get("n").asInt()).isEqualTo(1);
        assertThat(json.readTree(messages.get(1)).get("payload").get("n").asInt()).isEqualTo(2);
    }

    @Test
    void redisError_StillDeliversLocally_AndIsCounted() {
        when(redis.convertAndSend(anyString(), anyString())).thenThrow(new RedisConnectionFailureException("down"));

        liveUpdates.publish("/topic/x", Map.of("status", "SENT"));

        verify(local).convertAndSend(eq("/topic/x"), any(Object.class));
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(failures("redis_error")).isEqualTo(1));
    }

    @Test
    void hangingRedis_DoesNotDelayPublish() {
        blockRedis();

        long start = System.nanoTime();
        liveUpdates.publish("/topic/x", Map.of("status", "SENT"));

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(200));
    }

    @Test
    void fullQueue_DropsAndCounts() {
        relay = new RedisLiveUpdateRelay(redis, local, json, meters, 1);
        liveUpdates = new LiveUpdates(local, relay);
        blockRedis();

        liveUpdates.publish("/topic/x", Map.of("n", 1)); // taken by the sender, which blocks
        verify(redis, timeout(2000)).convertAndSend(eq(CHANNEL), anyString());
        liveUpdates.publish("/topic/x", Map.of("n", 2)); // waits in the queue
        liveUpdates.publish("/topic/x", Map.of("n", 3)); // queue full: dropped

        assertThat(failures("queue_full")).isEqualTo(1);
    }

    @Test
    void ownEnvelope_IsSkipped() {
        relay.onMessage(message(relay.instanceId(), "/topic/x"), null);

        verify(local, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void foreignEnvelope_IsDeliveredToItsDestination() {
        relay.onMessage(message("another-instance", "/topic/x"), null);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(local).convertAndSend(eq("/topic/x"), payload.capture());
        assertThat(((JsonNode) payload.getValue()).get("status").asText()).isEqualTo("SENT");
    }

    @Test
    void malformedEnvelope_IsDropped() {
        relay.onMessage(new DefaultMessage(CHANNEL.getBytes(StandardCharsets.UTF_8),
                "not json".getBytes(StandardCharsets.UTF_8)), null);

        verify(local, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void destroy_ShutsTheSenderDown() throws Exception {
        relay.destroy();

        relay.forward("/topic/x", Map.of("status", "SENT"));

        assertThat(failures("queue_full")).isEqualTo(1);
    }

    private void blockRedis() {
        when(redis.convertAndSend(anyString(), anyString())).thenAnswer(invocation -> {
            redisBlocked.await(10, TimeUnit.SECONDS);
            return 1L;
        });
    }

    private double failures(String reason) {
        var counter = meters.find("pulse.live.updates.forward.failed").tag("reason", reason).counter();
        return counter == null ? 0 : counter.count();
    }

    private DefaultMessage message(String origin, String destination) {
        String body = """
                {"origin":"%s","destination":"%s","payload":{"status":"SENT"}}""".formatted(origin, destination);
        return new DefaultMessage(CHANNEL.getBytes(StandardCharsets.UTF_8), body.getBytes(StandardCharsets.UTF_8));
    }
}
