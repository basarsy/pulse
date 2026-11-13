package com.pulse.connected;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the whole application against real Postgres (Flyway migrations + Hibernate validation)
 * and Redis, with an embedded Kafka broker, and drives the main flows over HTTP.
 */
@SpringBootTest(properties = {
        "pulse.simulator.enabled=false",
        "pulse.security.jwt.secret=smoke-test-secret-key-that-is-at-least-32-bytes"
})
@AutoConfigureMockMvc
@Testcontainers
@EmbeddedKafka(partitions = 1, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class ApplicationSmokeTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void ownerCanRegisterCreateVehicleAndIssueCommand() throws Exception {
        JsonNode auth = register("owner-" + UUID.randomUUID() + "@pulse.test");
        String accessToken = auth.get("accessToken").asText();

        String vehicleId = createVehicle(accessToken);

        String command = mvc.perform(post("/api/v1/vehicles/{id}/commands", vehicleId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"LOCK\",\"idempotencyKey\":\"smoke-1\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String commandId = json.readTree(command).get("id").asText();

        mvc.perform(get("/api/v1/vehicles/{id}/commands/{commandId}", vehicleId, commandId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void publicRegistrationCannotGrantAdminRoles() throws Exception {
        String body = """
                {"email":"%s","password":"secret123","fullName":"Mallory","roles":["SYSTEM_ADMIN","FLEET_ADMIN"]}
                """.formatted("mallory-" + UUID.randomUUID() + "@pulse.test");

        String response = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode roles = json.readTree(response).at("/user/roles");
        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).asText()).isEqualTo("OWNER");
    }

    @Test
    void refreshTokenIsNotAcceptedAsBearerToken() throws Exception {
        JsonNode auth = register("refresh-" + UUID.randomUUID() + "@pulse.test");

        mvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + auth.get("refreshToken").asText()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + auth.get("accessToken").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void userCannotReadOrCancelAnotherOwnersCommands() throws Exception {
        String ownerToken = register("owner-" + UUID.randomUUID() + "@pulse.test").get("accessToken").asText();
        String vehicleId = createVehicle(ownerToken);
        String command = mvc.perform(post("/api/v1/vehicles/{id}/commands", vehicleId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"UNLOCK\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String commandId = json.readTree(command).get("id").asText();

        String attackerToken = register("attacker-" + UUID.randomUUID() + "@pulse.test").get("accessToken").asText();
        String attackerVehicleId = createVehicle(attackerToken);

        // Through the victim's vehicle: no access to that vehicle.
        mvc.perform(delete("/api/v1/vehicles/{id}/commands/{commandId}", vehicleId, commandId)
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isForbidden());

        // Through the attacker's own vehicle: the command does not belong to it.
        mvc.perform(get("/api/v1/vehicles/{id}/commands/{commandId}", attackerVehicleId, commandId)
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/vehicles/{id}/commands/{commandId}", attackerVehicleId, commandId)
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNotFound());
    }

    private JsonNode register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"secret123","fullName":"Smoke Test"}
                """.formatted(email);
        String response = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private String createVehicle(String accessToken) throws Exception {
        String vin = ("WBA" + UUID.randomUUID().toString().replace("-", "")).substring(0, 17).toUpperCase();
        String response = mvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vin\":\"%s\",\"model\":\"BMW iX xDrive50\",\"modelYear\":2026}".formatted(vin)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asText();
    }
}
