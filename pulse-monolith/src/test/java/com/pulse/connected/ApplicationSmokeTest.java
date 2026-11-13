package com.pulse.connected;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the whole application (Flyway migrations, Hibernate validation, bean wiring) and drives
 * the main HTTP flows, including the security rules.
 */
class ApplicationSmokeTest extends AbstractIntegrationTest {

    @Test
    void ownerCanRegisterCreateVehicleAndIssueCommand() throws Exception {
        String accessToken = registerAndGetToken("owner");
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
                """.formatted(uniqueEmail("mallory"));

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
        JsonNode auth = register(uniqueEmail("refresh"));

        mvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + auth.get("refreshToken").asText()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + auth.get("accessToken").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void userCannotReadOrCancelAnotherOwnersCommands() throws Exception {
        String ownerToken = registerAndGetToken("owner");
        String vehicleId = createVehicle(ownerToken);
        String command = mvc.perform(post("/api/v1/vehicles/{id}/commands", vehicleId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"UNLOCK\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String commandId = json.readTree(command).get("id").asText();

        String attackerToken = registerAndGetToken("attacker");
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
}
