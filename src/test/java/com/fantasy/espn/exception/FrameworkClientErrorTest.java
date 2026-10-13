package com.fantasy.espn.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A request Spring turns away before it reaches our code (a required parameter missing, a value
 * over its {@code @Size}, a method the path does not serve) is the caller's mistake and answers
 * with that 4xx. The catch-all used to take these for faults: a 500, and an ERROR with a stack
 * trace in Sentry. Full context on purpose: only the real, proxied controller validates its
 * parameters the way production does.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FrameworkClientErrorTest {

    private static final String ROSTERS = "/api/v1/espn/leagues/{leagueId}/rosters";

    @Autowired
    private MockMvc mockMvc;

    @Value("${internal.api-key}")
    private String internalApiKey;

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private final Logger handlerLog = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @BeforeEach
    void captureHandlerLog() {
        logged.start();
        handlerLog.addAppender(logged);
    }

    @AfterEach
    void releaseHandlerLog() {
        handlerLog.detachAppender(logged);
    }

    @Test
    void aMissingRequiredParameter_isBadRequest() throws Exception {
        mockMvc.perform(authorized(get(ROSTERS, "123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));

        assertNothingLoggedAsAnError();
    }

    @Test
    void aMissingRequiredNumber_isBadRequest() throws Exception {
        mockMvc.perform(authorized(get("/api/v1/espn/players/skaters")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertNothingLoggedAsAnError();
    }

    @Test
    void aValueOverItsSize_isBadRequestWithoutEchoingIt() throws Exception {
        String oversized = "9".repeat(21);

        mockMvc.perform(authorized(get(ROSTERS, oversized).param("appUserId", "u1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("leagueId")))
                .andExpect(jsonPath("$.message", not(containsString(oversized))));

        assertNothingLoggedAsAnError();
    }

    @Test
    void aMethodThePathDoesNotServe_isMethodNotAllowed() throws Exception {
        mockMvc.perform(authorized(post(ROSTERS, "123").param("appUserId", "u1")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "GET"))
                .andExpect(jsonPath("$.status").value(405));

        assertNothingLoggedAsAnError();
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
        return request.header("X-Internal-Api-Key", internalApiKey);
    }

    private void assertNothingLoggedAsAnError() {
        assertThat(logged.list)
                .filteredOn(event -> event.getLevel().isGreaterOrEqual(Level.ERROR))
                .isEmpty();
    }
}
