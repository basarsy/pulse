package com.pulse.connected;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for tests that boot the whole application against real Postgres and Redis
 * (Testcontainers) and an embedded Kafka broker. All subclasses share one configuration, so
 * Spring caches and reuses a single application context and a single set of containers.
 *
 * <p>The vehicle simulator runs with fast timings and no injected failures, so flows through
 * Kafka complete deterministically within a few seconds.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "pulse.security.jwt.secret=integration-test-secret-key-at-least-32-bytes",
        "pulse.simulator.enabled=true",
        "pulse.simulator.heartbeat-interval-ms=500",
        "pulse.simulator.telemetry-interval-ms=60000",
        "pulse.simulator.min-latency-ms=10",
        "pulse.simulator.latency-jitter-ms=0",
        "pulse.simulator.ota-step-delay-ms=10",
        "pulse.simulator.command-failure-rate=0",
        "pulse.simulator.ota-failure-rate=0"
})
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
public abstract class AbstractIntegrationTest {

    // Singleton containers: started once per JVM and shared by every subclass.
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    protected JsonNode register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"secret123","fullName":"Integration Test"}
                """.formatted(email);
        String response = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    protected JsonNode login(String email) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"secret123\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    protected String registerAndGetToken(String emailPrefix) throws Exception {
        return register(uniqueEmail(emailPrefix)).get("accessToken").asText();
    }

    protected String createVehicle(String accessToken) throws Exception {
        return createVehicle(accessToken, "BMW iX xDrive50");
    }

    protected String createVehicle(String accessToken, String model) throws Exception {
        String vin = ("WBA" + UUID.randomUUID().toString().replace("-", "")).substring(0, 17).toUpperCase();
        String response = mvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vin\":\"%s\",\"model\":\"%s\",\"modelYear\":2026}".formatted(vin, model)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asText();
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@pulse.test";
    }
}
