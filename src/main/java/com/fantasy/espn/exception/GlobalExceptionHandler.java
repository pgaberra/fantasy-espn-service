package com.fantasy.espn.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EspnLeagueNotFoundException.class)
    public ResponseEntity<ErrorDto> handleNotFound(EspnLeagueNotFoundException e) {
        // Expected client outcome (no such ESPN league/season), not a server fault.
        return build(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(EspnPrivateLeagueException.class)
    public ResponseEntity<ErrorDto> handlePrivateLeague(EspnPrivateLeagueException e) {
        // Expected client outcome (league is private / cookies missing or invalid), not a fault.
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDto> handleValidation(MethodArgumentNotValidException e) {
        // Expected client outcome (invalid request body), not a server fault.
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .orElse("Validation failed");
        return build(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorDto> handleBadRequest(IllegalArgumentException e) {
        // Expected client outcome (e.g. malformed league id / season), not a server fault.
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorDto> handleUpstreamFailure(IllegalStateException e) {
        // Raised when the upstream ESPN API call fails — a real failure, so log it.
        log.error("Upstream ESPN API call failed", e);
        return build(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> handleUnexpected(Exception e) {
        // An unmatched exception must never be silently swallowed — log the full trace.
        log.error("Unhandled exception", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<ErrorDto> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ErrorDto.of(status.value(), status.getReasonPhrase(), message));
    }
}
