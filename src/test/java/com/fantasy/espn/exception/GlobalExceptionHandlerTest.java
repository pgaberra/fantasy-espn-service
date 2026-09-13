package com.fantasy.espn.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(handler)
            .build();

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

    @Test
    void anEspnFailureIsA502() throws Exception {
        mvc.perform(get("/upstream"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value("ESPN API call failed (HTTP 503)"));
    }

    @Test
    void anIllegalStateExceptionIsThisServicesFaultNotEspns() throws Exception {
        // A missing TOKEN_ENCRYPTION_KEY used to answer 502 and log an ESPN outage on every
        // league read for a user with stored cookies, although ESPN was never called.
        mvc.perform(get("/local-fault"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/upstream")
        String upstream() {
            throw new EspnUpstreamException("ESPN API call failed (HTTP 503)");
        }

        @GetMapping("/local-fault")
        String localFault() {
            throw new IllegalStateException("Failed to decrypt cookie");
        }
    }
}
