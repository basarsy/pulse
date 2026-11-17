package com.pulse.connected;

import com.fasterxml.jackson.databind.JsonNode;
import com.pulse.connected.auth.Role;
import com.pulse.connected.auth.User;
import com.pulse.connected.auth.UserRepository;
import com.pulse.connected.domain.CampaignStatus;
import com.pulse.connected.domain.CommandStatus;
import com.pulse.connected.domain.UpdateStatus;
import com.pulse.connected.websocket.LiveUpdates;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every live update carries the version of the state it shows, so a browser can drop one that
 * arrives after a newer one. For each topic, over a real STOMP connection: versions are distinct,
 * a higher version never shows an earlier state, and the highest equals what a REST read returns
 * afterwards. Arrival order is not asserted: it is exactly what can't be relied on.
 */
class LiveUpdateVersionsTest extends AbstractIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @LocalServerPort
    private int port;

    @Autowired
    private LiveUpdates liveUpdates;

    @Autowired
    private UserRepository userRepository;

    private final List<StompSession> sessions = new ArrayList<>();

    @AfterEach
    void disconnect() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
    }

    @Test
    void commandUpdatesCarryIncreasingVersions() throws Exception {
        String token = registerAndGetToken("versions-owner");
        String vehicleId = createVehicle(token);
        awaitOnline(token, vehicleId);
        Queue<Map<String, Object>> updates = subscribe(token, "/topic/vehicles/" + vehicleId + "/commands");

        String commandId = json.readTree(mvc.perform(post("/api/v1/vehicles/{id}/commands", vehicleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"LOCK\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        List<Map<String, Object>> received = awaitUntil(updates, u -> "COMPLETED".equals(u.get("status")));
        JsonNode stored = getJson(token, "/api/v1/vehicles/" + vehicleId + "/commands/" + commandId);

        assertVersionsFollowState(received,
                Comparator.comparing(u -> CommandStatus.valueOf((String) u.get("status"))), stored);
    }

    @Test
    void otaAndRolloutUpdatesCarryIncreasingVersions() throws Exception {
        String token = fleetAdminToken();
        String model = "Versions " + UUID.randomUUID();
        List<String> vehicleIds = List.of(createVehicle(token, model), createVehicle(token, model));
        for (String vehicleId : vehicleIds) {
            awaitOnline(token, vehicleId);
        }
        String campaignId = createRollout(token, model);

        Queue<Map<String, Object>> rolloutUpdates = subscribe(token, "/topic/rollouts/" + campaignId);
        List<Queue<Map<String, Object>>> vehicleUpdates = new ArrayList<>();
        for (String vehicleId : vehicleIds) {
            vehicleUpdates.add(subscribe(token, "/topic/vehicles/" + vehicleId + "/update-status"));
        }

        mvc.perform(post("/api/v1/rollouts/{id}/start", campaignId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // A rollout's later state: further in its status, then further in its stages, then more
        // vehicles finished.
        List<Map<String, Object>> rollout = awaitUntil(rolloutUpdates, u -> "COMPLETED".equals(u.get("status")));
        assertVersionsFollowState(rollout,
                Comparator.<Map<String, Object>, CampaignStatus>comparing(u -> CampaignStatus.valueOf((String) u.get("status")))
                        .thenComparing(u -> number(u, "currentStageIndex"))
                        .thenComparing(u -> number(u, "vehiclesInstalled") + number(u, "vehiclesFailed")),
                getJson(token, "/api/v1/rollouts/" + campaignId));

        for (int i = 0; i < vehicleIds.size(); i++) {
            List<Map<String, Object>> vehicle = awaitUntil(vehicleUpdates.get(i), u -> "INSTALLED".equals(u.get("status")));
            JsonNode stored = getJson(token, "/api/v1/vehicles/" + vehicleIds.get(i) + "/update-status").get(0);
            assertVersionsFollowState(vehicle,
                    Comparator.<Map<String, Object>, UpdateStatus>comparing(u -> UpdateStatus.valueOf((String) u.get("status")))
                            .thenComparing(u -> number(u, "progressPercent")),
                    stored);
        }
    }

    /**
     * Sorted by version, the updates never step back to an earlier state; no two share a version;
     * and the highest version is the one stored.
     */
    private static void assertVersionsFollowState(List<Map<String, Object>> updates,
                                                  Comparator<Map<String, Object>> stateOrder, JsonNode stored) {
        assertThat(updates).as("updates").isNotEmpty();
        assertThat(updates).as("every update has a version").allSatisfy(u -> assertThat(u).containsKey("version"));

        List<Map<String, Object>> byVersion = updates.stream()
                .sorted(Comparator.comparingLong(u -> number(u, "version")))
                .toList();
        List<String> summary = byVersion.stream().map(u -> u.get("version") + ":" + u.get("status")).toList();
        Set<Long> versions = new HashSet<>();
        for (int i = 0; i < byVersion.size(); i++) {
            assertThat(versions.add(number(byVersion.get(i), "version")))
                    .as("version %s is used once: %s", byVersion.get(i).get("version"), summary).isTrue();
            if (i > 0) {
                assertThat(stateOrder.compare(byVersion.get(i - 1), byVersion.get(i)))
                        .as("a higher version never shows an earlier state: %s", summary).isLessThanOrEqualTo(0);
            }
        }
        assertThat(number(byVersion.get(byVersion.size() - 1), "version"))
                .as("the highest version is the stored one: %s", summary).isEqualTo(stored.get("version").asLong());
    }

    private static long number(Map<String, Object> update, String field) {
        Object value = update.get(field);
        return value == null ? 0 : ((Number) value).longValue();
    }

    /** Collects real updates (not the readiness probe) until one matches {@code last}. */
    private static List<Map<String, Object>> awaitUntil(Queue<Map<String, Object>> queue,
                                                        Function<Map<String, Object>, Boolean> last) {
        List<Map<String, Object>> received = new ArrayList<>();
        await().atMost(TIMEOUT).untilAsserted(() -> {
            for (Map<String, Object> update; (update = queue.poll()) != null; ) {
                if (!update.containsKey("probe")) {
                    received.add(update);
                }
            }
            assertThat(received).anyMatch(last::apply);
        });
        return received;
    }

    /** Subscribes and waits until the subscription is live (a probe published to it comes back). */
    private Queue<Map<String, Object>> subscribe(String token, String topic) throws Exception {
        Queue<Map<String, Object>> queue = new ConcurrentLinkedQueue<>();
        connect(token).subscribe(topic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((Map<String, Object>) payload);
            }
        });
        await().atMost(TIMEOUT).until(() -> {
            liveUpdates.publish(topic, Map.of("probe", true));
            TimeUnit.MILLISECONDS.sleep(200);
            return queue.stream().anyMatch(u -> u.containsKey("probe"));
        });
        return queue;
    }

    private StompSession connect(String accessToken) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + accessToken);
        StompSession session = client.connectAsync("ws://localhost:{port}/ws-pulse/websocket",
                        new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {}, port)
                .get(10, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    private String fleetAdminToken() throws Exception {
        String email = uniqueEmail("versions-admin");
        register(email);
        User user = userRepository.findByEmail(email).orElseThrow();
        Set<Role> roles = new HashSet<>(user.getRoles());
        roles.add(Role.FLEET_ADMIN);
        user.setRoles(roles);
        userRepository.save(user);
        return login(email).get("accessToken").asText();
    }

    private String createRollout(String token, String model) throws Exception {
        String label = "2026." + UUID.randomUUID().toString().substring(0, 8);
        String versionId = json.readTree(mvc.perform(post("/api/v1/software-versions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"versionLabel":"%s","checksum":"sha256:abc","packageUrl":"https://ota.pulse.test/%s.bin"}
                                """.formatted(label, label)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        return json.readTree(mvc.perform(post("/api/v1/rollouts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"softwareVersionId":"%s","targetModel":"%s","stagePercentages":[50,100],"failureThresholdPercent":10}
                                """.formatted(versionId, model)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private void awaitOnline(String token, String vehicleId) {
        await().atMost(TIMEOUT).until(() ->
                "ONLINE".equals(getJson(token, "/api/v1/vehicles/" + vehicleId).get("connectivityState").asText()));
    }

    private JsonNode getJson(String token, String path) throws Exception {
        return json.readTree(mvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
