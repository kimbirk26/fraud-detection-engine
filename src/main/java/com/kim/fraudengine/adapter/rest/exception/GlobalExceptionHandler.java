package com.kim.fraudengine.adapter.rest.exception;

import com.kim.fraudengine.adapter.rest.dto.ErrorResponse;
import com.kim.fraudengine.infrastructure.logging.SensitiveLogValueSanitizer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@SuppressFBWarnings(
        value = "CRLF_INJECTION_LOGS",
        justification =
                "All exception messages pass through SensitiveLogValueSanitizer.normalizeForLog() "
                        + "which strips control characters; SpotBugs does not recognise custom sanitizers as taint-cleaners")
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AlertNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAlertNotFound(AlertNotFoundException ex) {
        String traceId = newTraceId();
        log.info(
                "Alert not found [traceId={}]: {}",
                traceId,
                SensitiveLogValueSanitizer.normalizeForLog(ex.getMessage()));

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Not Found", ex.getMessage(), traceId));
    }

    @ExceptionHandler(InvalidFilterException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFilter(InvalidFilterException ex) {
        String traceId = newTraceId();
        log.info(
                "Invalid filter [traceId={}]: {}",
                traceId,
                SensitiveLogValueSanitizer.normalizeForLog(ex.getMessage()));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, "Bad Request", ex.getMessage(), traceId));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex) {
        String traceId = newTraceId();

        List<ErrorResponse.FieldError> fieldErrors =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(fe -> new ErrorResponse.FieldError(fe.getField(), fieldMessage(fe)))
                        .toList();

        log.info(
                "Validation failed [traceId={}]: {} field errors",
                traceId,
                fieldErrors.size()); // fieldErrors.size() is an int - safe

        return ResponseEntity.badRequest().body(ErrorResponse.validation(traceId, fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex) {
        String traceId = newTraceId();

        List<ErrorResponse.FieldError> fieldErrors =
                ex.getConstraintViolations().stream()
                        .map(
                                cv -> {
                                    String path = cv.getPropertyPath().toString();
                                    String field =
                                            path.contains(".")
                                                    ? path.substring(path.lastIndexOf('.') + 1)
                                                    : path;
                                    return new ErrorResponse.FieldError(field, cv.getMessage());
                                })
                        .toList();

        log.info(
                "Constraint violation [traceId={}]: {}",
                traceId,
                SensitiveLogValueSanitizer.normalizeForLog(ex.getMessage()));

        return ResponseEntity.badRequest().body(ErrorResponse.validation(traceId, fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException ex) {
        String traceId = newTraceId();

        String safeMessage = extractSafeMessage(ex);

        log.info(
                "Unreadable request body [traceId={}]: {}",
                traceId,
                SensitiveLogValueSanitizer.normalizeForLog(ex.getMessage()));

        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "Bad Request", safeMessage, traceId));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        String traceId = newTraceId();
        Class<?> requiredType = ex.getRequiredType();

        String message =
                String.format(
                        "Invalid value '%s' for parameter '%s'%s.",
                        ex.getValue(),
                        ex.getName(),
                        requiredType != null ? " — expected " + requiredType.getSimpleName() : "");

        log.info(
                "Type mismatch [traceId={}]: {}",
                traceId,
                SensitiveLogValueSanitizer.normalizeForLog(message));

        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "Bad Request", message, traceId));
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ErrorResponse> handleAccessDenied(Exception ex) {
        String traceId = newTraceId();

        log.warn(
                "Access denied [traceId={}]: {}",
                traceId,
                SensitiveLogValueSanitizer.normalizeForLog(ex.getMessage()));

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(403, "Forbidden", "Access is denied.", traceId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        String traceId = newTraceId();

        log.error("Unexpected error [traceId={}]", traceId, ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        ErrorResponse.of(
                                500,
                                "Internal Server Error",
                                "An unexpected error occurred. Reference: " + traceId,
                                traceId));
    }

    private String newTraceId() {
        String correlationId = MDC.get("correlationId");
        if (correlationId != null && !correlationId.isBlank()) {
            return correlationId;
        }
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String fieldMessage(FieldError fe) {
        String msg = fe.getDefaultMessage();
        return (msg != null && !msg.isBlank()) ? msg : "Invalid value.";
    }

    private String extractSafeMessage(HttpMessageNotReadableException ex) {
        String cause = ex.getMessage();

        if (cause == null) {
            return "Request body could not be parsed.";
        }

        if (cause.contains("not one of the values accepted for Enum class")) {
            return "Invalid enum value in request body. Check the API documentation for accepted values.";
        }

        if (cause.contains("Required request body is missing")) {
            return "Request body is required but was not provided.";
        }

        if (cause.contains("JSON parse error")) {
            return "Request body contains invalid JSON.";
        }

        return "Request body could not be parsed. Check the format and field types.";
    }
}
