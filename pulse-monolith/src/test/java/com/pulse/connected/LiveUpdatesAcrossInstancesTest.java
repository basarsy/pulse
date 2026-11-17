package com.pulse.connected;

import com.pulse.connected.command.CommandWebSocketService;
import com.pulse.connected.command.dto.CommandResponse;
import com.pulse.connected.domain.CommandStatus;
import com.pulse.connected.domain.CommandType;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.GenericContainer;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Two running instances of the app, sharing Postgres, Redis and Kafka like two pods. The STOMP
 * broker is in-memory per instance, so without the Redis relay a browser connected to instance B
 * never sees an update produced on instance A.
 */
class LiveUpdatesAcrossInstancesTest extends AbstractIntegrationTest {

    private static final List<ConfigurableApplicationContext> INSTANCES = new CopyOnWriteArrayList<>();
    private static ConfigurableApplicationContext instanceB;

    @LocalServerPort
    private int port;

    @Autowired
    private Environment environment;

    @Autowired
    private CommandWebSocketService commandWebSocketService;

    private final List<StompSession> sessions = new ArrayList<>();

    @AfterEach
    void disconnect() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
    }

    @AfterAll
    static void stopInstances() {
        INSTANCES.forEach(ConfigurableApplicationContext::close);
        INSTANCES.clear();
        instanceB = null;
        PASSWORD_REDIS.stop();
    }

    @Test
    void updatePublishedOnA_ReachesSubscribersOnBothInstances_ExactlyOnce() throws Exception {
        String token = registerAndGetToken("ws-multi");
        String vehicleId = createVehicle(token);
        String topic = "/topic/vehicles/" + vehicleId + "/commands";
        ConfigurableApplicationContext b = instanceB();

        BlockingQueue<Map<String, Object>> onA = subscribe(port, token, topic);
        BlockingQueue<Map<String, Object>> onB = subscribe(localPort(b), token, topic);

        // SUBSCRIBE is processed asynchronously and the simple broker sends no receipts, so probe
        // until both clients receive, then count only the frames of the real update.
        UUID vehicle = UUID.fromString(vehicleId);
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200)).until(() -> {
            commandWebSocketService.notifyCommandStatusChange(vehicle, update(vehicle)); // on A
            return !onA.isEmpty() && !onB.isEmpty();
        });

        CommandResponse update = update(vehicle);
        commandWebSocketService.notifyCommandStatusChange(vehicle, update); // on A

        List<Map<String, Object>> fromA = framesFor(update.id(), onA);
        List<Map<String, Object>> fromB = framesFor(update.id(), onB);
        assertThat(fromA).hasSize(1); // exactly once on each instance
        assertThat(fromB).hasSize(1);
        assertThat(fromB.get(0)).isEqualTo(fromA.get(0)); // same JSON, Instants and nulls included
    }

    @Test
    void updatesKeepTheirOrderOnTheReceivingInstance() throws Exception {
        // The dashboard keeps the last update it receives, so SENT arriving after DELIVERED on the
        // other pod would leave a command showing SENT for good.
        String token = registerAndGetToken("ws-order");
        UUID vehicle = UUID.fromString(createVehicle(token));
        BlockingQueue<Map<String, Object>> onB = subscribe(localPort(instanceB()), token,
                "/topic/vehicles/" + vehicle + "/commands");
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200)).until(() -> {
            commandWebSocketService.notifyCommandStatusChange(vehicle, update(vehicle)); // probe
            return !onB.isEmpty();
        });

        List<String> sent = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            CommandResponse update = update(vehicle);
            sent.add(update.id().toString());
            commandWebSocketService.notifyCommandStatusChange(vehicle, update); // on A, back to back
        }

        List<Object> received = new ArrayList<>();
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (received.size() < sent.size() && System.nanoTime() < deadline) {
            Map<String, Object> frame = onB.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (frame != null && sent.contains(frame.get("id"))) {
                received.add(frame.get("id"));
            }
        }
        assertThat(received).containsExactlyElementsOf(sent);
    }

    @Test
    void updatesKeepTheirOrderOnTheSameInstance() throws Exception {
        // No Redis involved: A's own browsers. The broker sends to clients through a thread pool.
        String token = registerAndGetToken("ws-order-local");
        UUID vehicle = UUID.fromString(createVehicle(token));
        BlockingQueue<Map<String, Object>> onA = subscribe(port, token, "/topic/vehicles/" + vehicle + "/commands");
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200)).until(() -> {
            commandWebSocketService.notifyCommandStatusChange(vehicle, update(vehicle)); // probe
            return !onA.isEmpty();
        });

        List<String> sent = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            CommandResponse update = update(vehicle);
            sent.add(update.id().toString());
            commandWebSocketService.notifyCommandStatusChange(vehicle, update);
        }

        List<Object> received = new ArrayList<>();
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (received.size() < sent.size() && System.nanoTime() < deadline) {
            Map<String, Object> frame = onA.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (frame != null && sent.contains(frame.get("id"))) {
                received.add(frame.get("id"));
            }
        }
        assertThat(received).containsExactlyElementsOf(sent);
    }

    /** Instance B, shared by the tests: a second running app, subscribed to Redis. */
    private ConfigurableApplicationContext instanceB() {
        if (instanceB == null) {
            instanceB = startInstance(Map.of());
            await().atMost(Duration.ofSeconds(10))
                    .until(() -> instanceB.getBean(RedisMessageListenerContainer.class).isListening());
        }
        return instanceB;
    }

    private static CommandResponse update(UUID vehicleId) {
        return new CommandResponse(
                UUID.randomUUID(),
                vehicleId,
                null,
                CommandType.LOCK,
                CommandStatus.SENT,
                Instant.now(),
                null,
                null,
                null,
                null,
                0);
    }

    /** Everything received in the next 3 seconds that belongs to command {@code id}. */
    private static List<Map<String, Object>> framesFor(UUID id, BlockingQueue<Map<String, Object>> queue)
            throws InterruptedException {
        List<Map<String, Object>> frames = new ArrayList<>();
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            Map<String, Object> frame = queue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (frame != null && id.toString().equals(frame.get("id"))) {
                frames.add(frame);
            }
        }
        return frames;
    }

    @Test
    void instanceStartsWithoutRedis() {
        // Probes are only on by default inside Kubernetes; /health itself is DOWN without Redis.
        ConfigurableApplicationContext c = startInstance(Map.of(
                "spring.data.redis.port", "1",
                "management.endpoint.health.probes.enabled", "true"));

        String liveness = RestClient.create("http://localhost:" + localPort(c))
                .get().uri("/actuator/health/liveness").retrieve().body(String.class);
        assertThat(liveness).contains("UP");
    }

    @Test
    void instanceWithoutRedis_StillAcceptsCommands_AndStopsCallingRedis() throws Exception {
        ConfigurableApplicationContext c = startInstance(Map.of("spring.data.redis.port", "1"));
        String token = registerAndGetToken("no-redis");
        String vehicleId = createVehicle(token);
        RestClient api = RestClient.create("http://localhost:" + localPort(c));

        for (int i = 0; i < 6; i++) {
            HttpStatusCode status = api.post().uri("/api/v1/vehicles/{id}/commands", vehicleId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"type\":\"LOCK\"}")
                    .retrieve().toBodilessEntity().getStatusCode();
            assertThat(status.value()).isEqualTo(202);
        }

        assertThat(c.getBean(MeterRegistry.class).counter("pulse.commands.ratelimit.skipped").count()).isEqualTo(6);
        assertThat(c.getBean(CircuitBreakerRegistry.class).circuitBreaker("redisRateLimit").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);
    }

    /** Redis as deployed in Kubernetes: commands only with the password. */
    private static final GenericContainer<?> PASSWORD_REDIS = new GenericContainer<>("redis:7-alpine")
            .withCommand("redis-server", "--requirepass", "s3cret")
            .withExposedPorts(6379);

    @Test
    void instanceWithTheRedisPassword_RateLimitsThroughRedis() throws Exception {
        PASSWORD_REDIS.start();
        ConfigurableApplicationContext c = startInstance(passwordRedis("s3cret"));

        issueLock(c);

        assertThat(c.getBean(MeterRegistry.class).counter("pulse.commands.ratelimit.skipped").count()).isZero();
        assertThat(c.getBean(StringRedisTemplate.class).keys("ratelimit:commands:*")).isNotEmpty();
    }

    @Test
    void instanceWithAWrongRedisPassword_StillAcceptsCommands() throws Exception {
        PASSWORD_REDIS.start();
        ConfigurableApplicationContext c = startInstance(passwordRedis("wrong"));

        issueLock(c);

        assertThat(c.getBean(MeterRegistry.class).counter("pulse.commands.ratelimit.skipped").count()).isEqualTo(1);
        // Redis was up but refused the wrong password: nothing reached it.
        RedisClient admin = RedisClient.create(RedisURI.builder()
                .withHost(PASSWORD_REDIS.getHost())
                .withPort(PASSWORD_REDIS.getMappedPort(6379))
                .withPassword("s3cret".toCharArray())
                .build());
        try (var connection = admin.connect()) {
            assertThat(connection.sync().keys("ratelimit:commands:*")).isEmpty();
        } finally {
            admin.shutdown();
        }
    }

    private static Map<String, String> passwordRedis(String password) {
        // REDIS_PASSWORD, as Kubernetes passes it from app-secrets.
        return Map.of(
                "spring.data.redis.host", PASSWORD_REDIS.getHost(),
                "spring.data.redis.port", String.valueOf(PASSWORD_REDIS.getMappedPort(6379)),
                "REDIS_PASSWORD", password);
    }

    private void issueLock(ConfigurableApplicationContext c) throws Exception {
        String token = registerAndGetToken("redis-auth");
        String vehicleId = createVehicle(token);
        HttpStatusCode status = RestClient.create("http://localhost:" + localPort(c))
                .post().uri("/api/v1/vehicles/{id}/commands", vehicleId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"type\":\"LOCK\"}")
                .retrieve().toBodilessEntity().getStatusCode();
        assertThat(status.value()).isEqualTo(202);
    }

    private ConfigurableApplicationContext startInstance(Map<String, String> overrides) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("server.port", "0");
        properties.put("pulse.security.jwt.secret", "integration-test-secret-key-at-least-32-bytes");
        properties.put("spring.datasource.url", POSTGRES.getJdbcUrl());
        properties.put("spring.datasource.username", POSTGRES.getUsername());
        properties.put("spring.datasource.password", POSTGRES.getPassword());
        properties.put("spring.data.redis.host", REDIS.getHost());
        properties.put("spring.data.redis.port", String.valueOf(REDIS.getMappedPort(6379)));
        properties.put("spring.kafka.bootstrap-servers", environment.getProperty("spring.kafka.bootstrap-servers"));
        // A second member of the consumer groups would take partitions from instance A and
        // disturb the tests that run after this one.
        properties.put("spring.kafka.listener.auto-startup", "false");
        properties.put("pulse.simulator.enabled", "false");
        properties.put("spring.jmx.enabled", "false");
        properties.putAll(overrides);

        // As command-line arguments: builder.properties() only sets defaults, which application.yml overrides.
        String[] args = properties.entrySet().stream()
                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
        ConfigurableApplicationContext context = new SpringApplicationBuilder(PulseApplication.class).run(args);
        INSTANCES.add(context);
        return context;
    }

    private static int localPort(ConfigurableApplicationContext context) {
        return context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
    }

    private BlockingQueue<Map<String, Object>> subscribe(int instancePort, String accessToken, String topic)
            throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + accessToken);
        StompSession session = client.connectAsync("ws://localhost:{port}/ws-pulse/websocket",
                        new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {}, instancePort)
                .get(10, SECONDS);
        sessions.add(session);

        BlockingQueue<Map<String, Object>> updates = new LinkedBlockingQueue<>();
        session.subscribe(topic, collectInto(updates));
        return updates;
    }

    private static StompFrameHandler collectInto(BlockingQueue<Map<String, Object>> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((Map<String, Object>) payload);
            }
        };
    }
}
