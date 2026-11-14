package com.pulse.connected.ota.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;

/** Reports the first problem found, so the message says exactly what to fix. */
public class StagePercentagesValidator implements ConstraintValidator<ValidStagePercentages, List<Integer>> {

    private static final String WHOLE_NUMBERS = "stages must be whole numbers from 1 to 100";

    @Override
    public boolean isValid(List<Integer> stages, ConstraintValidatorContext context) {
        if (stages == null || stages.isEmpty()) {
            return true;
        }
        String problem = firstProblem(stages);
        if (problem == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(problem).addConstraintViolation();
        return false;
    }

    private static String firstProblem(List<Integer> stages) {
        for (Integer stage : stages) {
            if (stage == null) {
                return WHOLE_NUMBERS;
            }
            if (stage < 1 || stage > 100) {
                return WHOLE_NUMBERS + " (got " + stage + ")";
            }
        }
        for (int i = 1; i < stages.size(); i++) {
            if (stages.get(i) <= stages.get(i - 1)) {
                return "stages must increase: " + stages.get(i) + " follows " + stages.get(i - 1);
            }
        }
        if (stages.get(stages.size() - 1) != 100) {
            return "the last stage must be 100";
        }
        return null;
    }
}
