package com.fantasy.espn.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void aPathThisServiceDoesNotServeIsA404() {
        // Not a 500. A BFF newer than the deployed copy of this service called a path this
        // build had never heard of, and the catch-all turned every one of those into a server
        // fault with a stack trace in Sentry.
        NoResourceFoundException missing = new NoResourceFoundException(
                HttpMethod.GET, "api/v1/espn/players", "/api/v1/espn/players");

        ResponseEntity<ErrorDto> response = handler.handleNoResource(missing);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
    }
}
