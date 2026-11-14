package com.pulse.connected.command.dto;

import com.pulse.connected.domain.CommandType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IssueCommandRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void keyOf100Chars_IsValid() {
        assertThat(validator.validate(request("k".repeat(100)))).isEmpty();
    }

    @Test
    void keyOf101Chars_IsRejected() {
        // The column is VARCHAR(100): longer keys must be a 400, not a database error.
        Set<ConstraintViolation<IssueCommandRequest>> violations = validator.validate(request("k".repeat(101)));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("idempotencyKey");
    }

    private static IssueCommandRequest request(String key) {
        return IssueCommandRequest.builder().type(CommandType.LOCK).idempotencyKey(key).build();
    }
}
