package com.pulse.connected;

import com.fasterxml.jackson.databind.JsonNode;
import com.pulse.connected.auth.Role;
import com.pulse.connected.auth.User;
import com.pulse.connected.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives complete flows through Kafka: the API publishes an instruction, the vehicle simulator
 * consumes it and reports back, and the application consumes the result and updates its state.
 */
class KafkaRoundTripTest extends AbstractIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void commandToOnlineVehicleCompletesThroughKafka() throws Exception {
        String token = registerAndGetToken("owner");
        String vehicleId = createVehicle(token);
        await().atMost(TIMEOUT).until(() -> "ONLINE".equals(vehicle(token, vehicleId).get("connectivityState").asText()));

        String commandId = issueCommand(token, vehicleId, "LOCK");

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(command(token, vehicleId, commandId).get("status").asText()).isEqualTo("COMPLETED"));
        assertOutboxDrained();
    }

    @Test
    void commandIssuedBeforeFirstHeartbeatIsDeliveredOnceVehicleIsOnline() throws Exception {
        String token = registerAndGetToken("owner");
        String vehicleId = createVehicle(token);

        // Issued straight after registration, typically while the vehicle is still OFFLINE: the
        // command is queued and must be flushed when the vehicle's first heartbeat arrives.
        String commandId = issueCommand(token, vehicleId, "UNLOCK");

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(command(token, vehicleId, commandId).get("status").asText()).isEqualTo("COMPLETED"));
    }

    @Test
    void stagedOtaRolloutCompletesAcrossAllStages() throws Exception {
        String email = uniqueEmail("fleet-admin");
        register(email);
        promote(email, Role.FLEET_ADMIN);
        String token = login(email).get("accessToken").asText();

        String model = "Rollout Test " + UUID.randomUUID();
        createVehicle(token, model);
        createVehicle(token, model);

        String versionId = createSoftwareVersion(token);
        String campaignId = json.readTree(mvc.perform(post("/api/v1/rollouts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"softwareVersionId":"%s","targetModel":"%s","stagePercentages":[50,100],"failureThresholdPercent":10}
                                """.formatted(versionId, model)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        mvc.perform(post("/api/v1/rollouts/{id}/start", campaignId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        await().atMost(TIMEOUT).untilAsserted(() -> {
            JsonNode campaign = campaign(token, campaignId);
            assertThat(campaign.get("status").asText()).isEqualTo("COMPLETED");
            assertThat(campaign.get("vehiclesInstalled").asLong()).isEqualTo(2);
            assertThat(campaign.get("vehiclesFailed").asLong()).isZero();
        });
        assertOutboxDrained();

        JsonNode campaigns = getJson(token, "/api/v1/rollouts");
        assertThat(campaigns.findValuesAsText("id")).contains(campaignId);
    }

    /** Every outbox publication (command / OTA instruction) must eventually be sent to Kafka. */
    private void assertOutboxDrained() {
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_publication WHERE completion_date IS NULL", Integer.class)).isZero());
    }

    private String issueCommand(String token, String vehicleId, String type) throws Exception {
        String response = mvc.perform(post("/api/v1/vehicles/{id}/commands", vehicleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"%s\"}".formatted(type)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asText();
    }

    private JsonNode command(String token, String vehicleId, String commandId) throws Exception {
        return getJson(token, "/api/v1/vehicles/" + vehicleId + "/commands/" + commandId);
    }

    private JsonNode vehicle(String token, String vehicleId) throws Exception {
        return getJson(token, "/api/v1/vehicles/" + vehicleId);
    }

    private JsonNode campaign(String token, String campaignId) throws Exception {
        return getJson(token, "/api/v1/rollouts/" + campaignId);
    }

    private JsonNode getJson(String token, String path) throws Exception {
        String response = mvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private String createSoftwareVersion(String token) throws Exception {
        String label = "2026." + UUID.randomUUID().toString().substring(0, 8);
        String response = mvc.perform(post("/api/v1/software-versions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"versionLabel":"%s","checksum":"sha256:abc","packageUrl":"https://ota.pulse.test/%s.bin"}
                                """.formatted(label, label)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asText();
    }

    private void promote(String email, Role role) {
        User user = userRepository.findByEmail(email).orElseThrow();
        Set<Role> roles = new HashSet<>(user.getRoles());
        roles.add(role);
        user.setRoles(roles);
        userRepository.save(user);
    }
}
