package com.pulse.connected.ota.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rollout stages are cumulative shares of the target model's fleet: whole numbers from 1 to 100,
 * strictly increasing (a stage that doesn't grow instructs nobody), ending at 100 so every rollout
 * reaches the whole fleet. A missing or empty list is valid: it means the default stages.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StagePercentagesValidator.class)
public @interface ValidStagePercentages {

    String message() default "invalid stage percentages";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
