package com.kim.fraudengine.adapter.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(int status, String error, String message, String traceId, Instant timestamp,
                            List<FieldError> fieldErrors) {
    public ErrorResponse {
        fieldErrors = fieldErrors == null ? null : List.copyOf(fieldErrors);
    }

    public static ErrorResponse of(int status, String error, String message, String traceId) {
        return new ErrorResponse(status, error, message, traceId, Instant.now(), null);
    }

    public static ErrorResponse validation(String traceId, List<FieldError> fieldErrors) {
        return new ErrorResponse(400, "Bad Request", "Validation failed — see fieldErrors for details", traceId, Instant.now(), fieldErrors);
    }

    public record FieldError(String field, String message) {
    }
}
