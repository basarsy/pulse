package com.pulse.connected.ota;

import com.pulse.connected.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The stage rules are enforced by the endpoint itself, before the service is ever called. */
class RolloutCampaignControllerValidationTest {

    private final RolloutCampaignService service = mock(RolloutCampaignService.class);

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new RolloutCampaignController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void invalidStages_Return400WithReason() throws Exception {
        String body = """
                {"softwareVersionId":"%s","targetModel":"iX","stagePercentages":[50,25,100]}
                """.formatted(UUID.randomUUID());

        mvc.perform(post("/api/v1/rollouts").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("stagePercentages: stages must increase: 25 follows 50"));

        verifyNoInteractions(service);
    }

    @Test
    void fractionalStage_IsRejectedNotTruncated() throws Exception {
        // Jackson would otherwise turn 25.5 into 25 and create the campaign.
        expectInvalidValue("\"stagePercentages\":[5,25.5,100]", "stagePercentages: invalid value");
    }

    @Test
    void oversizedStage_Is400Not500() throws Exception {
        expectInvalidValue("\"stagePercentages\":[5,10000000000]", "stagePercentages: invalid value");
    }

    @Test
    void stageAsString_IsRejected() throws Exception {
        expectInvalidValue("\"stagePercentages\":[5,\"25\",100]", "stagePercentages: invalid value");
    }

    @Test
    void nonNumericThreshold_Is400Not500() throws Exception {
        expectInvalidValue("\"failureThresholdPercent\":\"abc\"", "failureThresholdPercent: invalid value");
    }

    private void expectInvalidValue(String field, String detail) throws Exception {
        String body = """
                {"softwareVersionId":"%s","targetModel":"iX",%s}
                """.formatted(UUID.randomUUID(), field);

        mvc.perform(post("/api/v1/rollouts").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"))
                .andExpect(jsonPath("$.details[0]").value(detail));

        verifyNoInteractions(service);
    }
}
