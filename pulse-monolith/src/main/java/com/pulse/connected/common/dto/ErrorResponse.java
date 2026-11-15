package com.pulse.connected.common.dto;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
        int status,
        String error,
        String message,
        LocalDateTime timestamp,
        List<String> details) {

    public static ErrorResponse of(HttpStatus status, String message) {
        return of(status, message, null);
    }

    public static ErrorResponse of(HttpStatus status, String message, List<String> details) {
        return new ErrorResponse(status.value(), status.getReasonPhrase(), message, LocalDateTime.now(), details);
    }
}
