package com.pulse.connected;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real STOMP clients over a real WebSocket: live command updates reach the vehicle's owner, and
 * unauthenticated or unauthorised clients are rejected.
 */
class LiveUpdatesWebSocketTest extends AbstractIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @LocalServerPort
    private int port;

    private final List<StompSession> sessions = new ArrayList<>();

    @AfterEach
    void disconnect() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
    }

    @Test
    void connectWithoutTokenIsRejected() {
        assertThatThrownBy(() -> connect(null)).isInstanceOf(ExecutionException.class);
    }

    @Test
    void connectWithRefreshTokenIsRejected() throws Exception {
        String refreshToken = register(uniqueEmail("ws-refresh")).get("refreshToken").asText();

        assertThatThrownBy(() -> connect(refreshToken)).isInstanceOf(ExecutionException.class);
    }

    @Test
    void ownerReceivesLiveCommandUpdatesForTheirVehicle() throws Exception {
        String token = registerAndGetToken("ws-owner");
        String vehicleId = createVehicle(token);

        BlockingQueue<Map<String, Object>> updates = new LinkedBlockingQueue<>();
        connect(token).subscribe("/topic/vehicles/" + vehicleId + "/commands", collectInto(updates));
        awaitOnline(token, vehicleId);

        mvc.perform(post("/api/v1/vehicles/{id}/commands", vehicleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"LOCK\"}"))
                .andExpect(status().isAccepted());

        List<Object> statuses = new ArrayList<>();
        await().atMost(TIMEOUT).untilAsserted(() -> {
            Map<String, Object> update = updates.poll(100, TimeUnit.MILLISECONDS);
            if (update != null) {
                statuses.add(update.get("status"));
            }
            assertThat(statuses).contains("COMPLETED");
        });
    }

    @Test
    void userCannotSubscribeToAnotherOwnersVehicle() throws Exception {
        String ownerToken = registerAndGetToken("ws-owner");
        String vehicleId = createVehicle(ownerToken);
        String strangerToken = registerAndGetToken("ws-stranger");

        StompSession stranger = connect(strangerToken);
        BlockingQueue<Map<String, Object>> leaked = new LinkedBlockingQueue<>();
        stranger.subscribe("/topic/vehicles/" + vehicleId + "/commands", collectInto(leaked));

        // The server answers a forbidden SUBSCRIBE with an ERROR frame and closes the session.
        await().atMost(TIMEOUT).until(() -> !stranger.isConnected());
        assertThat(leaked).isEmpty();
    }

    @Test
    void ownerCannotSubscribeToRolloutTopics() throws Exception {
        StompSession owner = connect(registerAndGetToken("ws-owner"));

        owner.subscribe("/topic/rollouts/*", collectInto(new LinkedBlockingQueue<>()));

        await().atMost(TIMEOUT).until(() -> !owner.isConnected());
    }

    private StompSession connect(String accessToken) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        if (accessToken != null) {
            connectHeaders.add("Authorization", "Bearer " + accessToken);
        }
        StompSession session = client.connectAsync("ws://localhost:{port}/ws-pulse/websocket",
                        new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {}, port)
                .get(10, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    private void awaitOnline(String token, String vehicleId) {
        await().atMost(TIMEOUT).until(() -> {
            String body = mvc.perform(get("/api/v1/vehicles/{id}", vehicleId).header("Authorization", "Bearer " + token))
                    .andReturn().getResponse().getContentAsString();
            return "ONLINE".equals(json.readTree(body).get("connectivityState").asText());
        });
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
