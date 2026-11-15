package com.pulse.connected;

import com.pulse.connected.auth.dto.LoginRequest;
import com.pulse.connected.auth.dto.RefreshTokenRequest;
import com.pulse.connected.auth.dto.RegisterRequest;
import com.pulse.connected.ota.dto.CreateSoftwareVersionRequest;
import com.pulse.connected.vehicle.dto.AuthorizeUserRequest;
import com.pulse.connected.vehicle.dto.CreateVehicleRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The constraints on request records must still reach Bean Validation: each valid request has no
 * violations, and breaking one field reports exactly that field's message.
 * (CreateCampaignRequest and IssueCommandRequest have their own tests.)
 */
class RequestValidationTest {

    private static final String VIN = "WVWZZZ1JZXW000001";

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    static Stream<Arguments> validRequests() {
        return Stream.of(
                Arguments.of(new LoginRequest("a@example.com", "secret")),
                Arguments.of(new RefreshTokenRequest("refresh-token")),
                Arguments.of(new RegisterRequest("a@example.com", "secret", "Ada Lovelace")),
                Arguments.of(new AuthorizeUserRequest("family@example.com")),
                Arguments.of(new CreateVehicleRequest(VIN, "Model X", 2024)),
                Arguments.of(new CreateSoftwareVersionRequest("v2.1.0", null, "sha256:abc", "https://example.com/v.bin", false)));
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of(new LoginRequest("not-an-email", "secret"), "email", "Invalid email format"),
                Arguments.of(new LoginRequest("a@example.com", " "), "password", "Password is required"),
                Arguments.of(new RefreshTokenRequest(""), "refreshToken", "Refresh token is required"),
                Arguments.of(new RegisterRequest("a@example.com", "12345", "Ada"), "password", "Password must be at least 6 characters"),
                Arguments.of(new RegisterRequest("a@example.com", "secret", null), "fullName", "Full name is required"),
                Arguments.of(new AuthorizeUserRequest("nope"), "userEmail", "Invalid email format"),
                Arguments.of(new CreateVehicleRequest("SHORTVIN", "Model X", 2024), "vin", "VIN must be exactly 17 characters"),
                Arguments.of(new CreateVehicleRequest(VIN, "Model X", 1800), "modelYear", "Invalid model year"),
                Arguments.of(new CreateVehicleRequest(VIN, "Model X", null), "modelYear", "Model year is required"),
                Arguments.of(new CreateSoftwareVersionRequest("v2.1.0", null, "", "https://example.com/v.bin", false), "checksum", "Checksum is required"));
    }

    @ParameterizedTest
    @MethodSource("validRequests")
    void validRequest_hasNoViolations(Object request) {
        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void invalidField_isReported(Object request, String field, String message) {
        assertThat(validator.validate(request))
                .extracting(v -> v.getPropertyPath().toString(), ConstraintViolation::getMessage)
                .containsExactly(tuple(field, message));
    }
}
