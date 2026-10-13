package com.fantasy.espn.league;

import com.fantasy.espn.exception.GlobalExceptionHandler;
import com.fantasy.espn.league.dto.DraftStatus;
import com.fantasy.espn.league.dto.LeagueRostersResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EspnLeagueControllerTest {

    private static final LeagueRostersResponse ROSTERS =
            new LeagueRostersResponse("123", 2027, null, DraftStatus.FINISHED, List.of());

    private final EspnLeagueService service = mock(EspnLeagueService.class);

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new EspnLeagueController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void rostersWithoutADateReadToday() throws Exception {
        when(service.rosters("u1", "123", null)).thenReturn(ROSTERS);

        mvc.perform(get("/api/v1/espn/leagues/123/rosters").param("appUserId", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leagueId").value("123"));

        verify(service).rosters(eq("u1"), eq("123"), isNull());
    }

    @Test
    void rostersForADayPassTheDayOn() throws Exception {
        LocalDate day = LocalDate.of(2026, 10, 14);
        when(service.rosters("u1", "123", day)).thenReturn(ROSTERS);

        mvc.perform(get("/api/v1/espn/leagues/123/rosters").param("appUserId", "u1").param("date", "2026-10-14"))
                .andExpect(status().isOk());

        verify(service).rosters("u1", "123", day);
    }

    @Test
    void aMalformedDateIsA400NotAServerFault() throws Exception {
        for (String malformed : List.of("2026-13-01", "14/10/2026", "tomorrow", "2026-10-14T00:00")) {
            mvc.perform(get("/api/v1/espn/leagues/123/rosters").param("appUserId", "u1").param("date", malformed))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("date must be a date in the form YYYY-MM-DD"));
        }

        verifyNoInteractions(service);
    }

    @Test
    void aDayOutsideTheSeasonIsA400() throws Exception {
        when(service.rosters(eq("u1"), eq("123"), any(LocalDate.class)))
                .thenThrow(new IllegalArgumentException("date 2027-06-01 is outside the 2026-27 season"));

        mvc.perform(get("/api/v1/espn/leagues/123/rosters").param("appUserId", "u1").param("date", "2027-06-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("date 2027-06-01 is outside the 2026-27 season"));
    }
}
