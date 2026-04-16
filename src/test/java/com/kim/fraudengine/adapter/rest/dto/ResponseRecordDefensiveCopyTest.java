package com.kim.fraudengine.adapter.rest.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResponseRecordDefensiveCopyTest {

    @Test
    void alertResponse_defensively_copies_triggered_rules() {
        List<AlertResponse.RuleResultResponse> triggeredRules = new ArrayList<>();
        triggeredRules.add(
                new AlertResponse.RuleResultResponse("RULE", SeverityResponse.HIGH, "reason"));

        AlertResponse response =
                new AlertResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "CUST001",
                        triggeredRules,
                        SeverityResponse.HIGH,
                        AlertStatusResponse.OPEN,
                        Instant.parse("2026-04-11T00:00:00Z"));

        triggeredRules.clear();

        assertThat(response.triggeredRules()).hasSize(1);
        assertThatThrownBy(
                        () ->
                                response.triggeredRules()
                                        .add(
                                                new AlertResponse.RuleResultResponse(
                                                        "OTHER", SeverityResponse.LOW, "other")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void errorResponse_defensively_copies_field_errors_when_present() {
        List<ErrorResponse.FieldError> fieldErrors = new ArrayList<>();
        fieldErrors.add(new ErrorResponse.FieldError("customerId", "must not be blank"));

        ErrorResponse response =
                new ErrorResponse(
                        400,
                        "Bad Request",
                        "Validation failed",
                        "TRACE1234",
                        Instant.parse("2026-04-11T00:00:00Z"),
                        fieldErrors);

        fieldErrors.clear();

        assertThat(response.fieldErrors()).hasSize(1);
        assertThatThrownBy(
                        () ->
                                response.fieldErrors()
                                        .add(
                                                new ErrorResponse.FieldError(
                                                        "amount", "must be positive")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
