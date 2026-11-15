package com.pulse.connected.ota.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreateCampaignRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void defaultStages_AreValid() {
        // Missing or empty means the default 5, 25, 100.
        assertThat(messages(null, 10.0)).isEmpty();
        assertThat(messages(List.of(), 10.0)).isEmpty();
    }

    @Test
    void validStages() {
        assertThat(messages(List.of(5, 25, 100), 10.0)).isEmpty();
        assertThat(messages(List.of(100), 10.0)).isEmpty();
        assertThat(messages(List.of(1, 100), 10.0)).isEmpty();
    }

    @Test
    void nullEntry_Rejected() {
        // The dashboard sends NaN as null; that must be a 400, not a 500 on insert.
        assertThat(messages(Arrays.asList(5, null, 100), 10.0))
                .containsExactly("stages must be whole numbers from 1 to 100");
    }

    @Test
    void outOfRange_Rejected() {
        assertThat(messages(List.of(0, 100), 10.0))
                .containsExactly("stages must be whole numbers from 1 to 100 (got 0)");
        assertThat(messages(List.of(5, 150), 10.0))
                .containsExactly("stages must be whole numbers from 1 to 100 (got 150)");
    }

    @Test
    void notIncreasing_Rejected() {
        assertThat(messages(List.of(50, 25, 100), 10.0)).containsExactly("stages must increase: 25 follows 50");
        // A repeated stage instructs nobody (stages are cumulative shares).
        assertThat(messages(List.of(25, 25, 100), 10.0)).containsExactly("stages must increase: 25 follows 25");
    }

    @Test
    void lastNotHundred_Rejected() {
        assertThat(messages(List.of(5, 25), 10.0)).containsExactly("the last stage must be 100");
    }

    @Test
    void threshold_Range() {
        assertThat(violations(List.of(100), -1.0)).singleElement()
                .satisfies(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("failureThresholdPercent"));
        assertThat(violations(List.of(100), 100.5)).singleElement()
                .satisfies(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("failureThresholdPercent"));
        assertThat(violations(List.of(100), 0.0)).isEmpty();
        assertThat(violations(List.of(100), 100.0)).isEmpty();
    }

    private List<String> messages(List<Integer> stages, Double threshold) {
        return violations(stages, threshold).stream().map(ConstraintViolation::getMessage).toList();
    }

    private List<ConstraintViolation<CreateCampaignRequest>> violations(List<Integer> stages, Double threshold) {
        CreateCampaignRequest request = new CreateCampaignRequest(UUID.randomUUID(), "BMW iX", threshold, stages);
        return List.copyOf(validator.validate(request));
    }
}
