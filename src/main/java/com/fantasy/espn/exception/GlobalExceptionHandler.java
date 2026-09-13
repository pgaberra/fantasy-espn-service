package com.fantasy.espn.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    /**
     * ESPN failed, so the answer is 502. Only the dedicated type gets here: this handler used to
     * take every IllegalStateException, which also caught this service's own faults (a missing
     * or wrong TOKEN_ENCRYPTION_KEY) and reported them as an ESPN outage although ESPN was never
     * called. Those now reach the catch-all as the 500 they are.
     */
    @ExceptionHandler(EspnUpstreamException.class)
    public ResponseEntity<ErrorDto> handleUpstreamFailure(EspnUpstreamException e) {
        log.error("Upstream ESPN API call failed", e);
        return build(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    /**
     * A request for a path this service does not serve. Without this the catch-all turns it
     * into a 500 with a full stack trace, which is wrong twice over: the caller asked for
     * something that isn't here, and a 404 is an ordinary answer rather than a fault worth
     * paging about. It fired for real — a BFF newer than the deployed copy of this service
     * called GET /api/v1/espn/players, and every page of players in production logged an
     * ERROR here and a second one in the BFF instead of one plain 404.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorDto> handleNoResource(NoResourceFoundException e) {
        return build(HttpStatus.NOT_FOUND, "No resource found for the requested path");
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
