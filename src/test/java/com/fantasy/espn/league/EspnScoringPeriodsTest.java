package com.fantasy.espn.league;

import com.fantasy.espn.config.EspnProperties;
import com.fantasy.espn.exception.EspnUpstreamException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EspnScoringPeriodsTest {

    private static final String SCHEDULE_URL = "/apis/v3/games/fhl/seasons/2027?view=proTeamSchedules_wl";

    /**
     * Period 1 has a 22:30 Eastern game, which is the next day in UTC. Oct 9 has no games but
     * periods 2 and 4 either side of it are two days apart; Oct 11-12 have none and the periods
     * either side of them are consecutive.
     */
    static final String SCHEDULE = """
            {"settings": {"proTeams": [
              {"id": 0, "proGamesByScoringPeriod": {}},
              {"id": 6, "proGamesByScoringPeriod": {
                "1": [%s],
                "2": [%s],
                "4": [%s],
                "6": [%s]
              }},
              {"id": 13, "proGamesByScoringPeriod": {
                "1": [%s],
                "5": [%s]
              }}
            ]}}
            """.formatted(
            game(2026, 10, 7, 19, 0),
            game(2026, 10, 8, 19, 0),
            game(2026, 10, 10, 15, 0),
            game(2026, 10, 14, 19, 30),
            game(2026, 10, 7, 22, 30),
            game(2026, 10, 13, 19, 0));

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-13T16:00:00Z"));
    private MockRestServiceServer server;
    private EspnScoringPeriods periods;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://espn.test");
        server = MockRestServiceServer.bindTo(builder).build();
        EspnProperties props = new EspnProperties("https://espn.test", "fhl", 2027, 2026, 2025, null);
        periods = new EspnScoringPeriods(new EspnFantasyClient(builder.build(), props), clock, props);
    }

    static String game(int year, int month, int day, int hour, int minute) {
        long start = LocalDateTime.of(year, month, day, hour, minute)
                .atZone(ZoneId.of("America/New_York")).toInstant().toEpochMilli();
        return "{\"date\": " + start + ", \"homeProTeamId\": 6, \"awayProTeamId\": 13}";
    }

    private void schedule(String json) {
        server.expect(requestTo(containsString(SCHEDULE_URL)))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Test
    void aGameDayIsThePeriodItsGamesAreFiledUnder() {
        schedule(SCHEDULE);

        assertThat(periods.periodOn(LocalDate.of(2026, 10, 8))).isEqualTo(2);
        assertThat(periods.periodOn(LocalDate.of(2026, 10, 13))).isEqualTo(5);
        assertThat(periods.periodOn(LocalDate.of(2026, 10, 14))).isEqualTo(6);
    }

    @Test
    void aLateGameCountsOnItsEasternDayNotOnTheUtcOne() {
        // 22:30 Eastern on Oct 7 is 02:30 UTC on Oct 8. Read in UTC, Oct 8 would hold games
        // of periods 1 and 2 and be refused.
        schedule(SCHEDULE);

        assertThat(periods.periodOn(LocalDate.of(2026, 10, 7))).isEqualTo(1);
        assertThat(periods.periodOn(LocalDate.of(2026, 10, 8))).isEqualTo(2);
    }

    @Test
    void aDayWithoutGamesHasAPeriodWhereEspnCountsEveryDay() {
        schedule(SCHEDULE);

        assertThat(periods.periodOn(LocalDate.of(2026, 10, 9))).isEqualTo(3);
    }

    @Test
    void aDayWithoutGamesThatEspnSkipsIsRefused() {
        schedule(SCHEDULE);

        assertThatThrownBy(() -> periods.periodOn(LocalDate.of(2026, 10, 11)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No NHL games are played on 2026-10-11");
    }

    @Test
    void aDayOutsideTheSeasonIsRefused() {
        schedule(SCHEDULE);

        assertThatThrownBy(() -> periods.periodOn(LocalDate.of(2026, 10, 6)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("date 2026-10-06 is outside the 2026-27 season, which ESPN scores "
                        + "from 2026-10-07 to 2026-10-14");
        assertThatThrownBy(() -> periods.periodOn(LocalDate.of(2026, 10, 15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside the 2026-27 season");
    }

    @Test
    void aDayWhoseGamesSitInTwoPeriodsIsEspnsFaultNotAGuess() {
        schedule("""
                {"settings": {"proTeams": [
                  {"id": 6, "proGamesByScoringPeriod": {"1": [%s], "2": [%s]}},
                  {"id": 13, "proGamesByScoringPeriod": {"2": [%s], "3": [%s]}}
                ]}}
                """.formatted(
                game(2026, 10, 7, 19, 0), game(2026, 10, 8, 19, 0),
                game(2026, 10, 7, 20, 0), game(2026, 10, 9, 19, 0)));

        assertThat(periods.periodOn(LocalDate.of(2026, 10, 9))).isEqualTo(3);
        assertThatThrownBy(() -> periods.periodOn(LocalDate.of(2026, 10, 7)))
                .isInstanceOf(EspnUpstreamException.class);
    }

    @Test
    void anEmptyScheduleIsEspnsFault() {
        schedule("{\"settings\": {\"proTeams\": [{\"id\": 0, \"proGamesByScoringPeriod\": {}}]}}");

        assertThatThrownBy(() -> periods.periodOn(LocalDate.of(2026, 10, 13)))
                .isInstanceOf(EspnUpstreamException.class)
                .hasMessageContaining("no NHL games");
    }

    @Test
    void aMissingScheduleIsEspnsFaultNotAMissingLeague() {
        server.expect(requestTo(containsString(SCHEDULE_URL))).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> periods.periodOn(LocalDate.of(2026, 10, 13)))
                .isInstanceOf(EspnUpstreamException.class);
    }

    @Test
    void theScheduleIsReadOnceAndAgainOnlyWhenItHasAged() {
        server.expect(ExpectedCount.once(), requestTo(containsString(SCHEDULE_URL)))
                .andRespond(withSuccess(SCHEDULE, MediaType.APPLICATION_JSON));

        periods.periodOn(LocalDate.of(2026, 10, 13));
        clock.advance(EspnScoringPeriods.MAX_AGE);
        periods.periodOn(LocalDate.of(2026, 10, 14));
        server.verify();

        server.reset();
        schedule(SCHEDULE);
        clock.advance(Duration.ofSeconds(1));
        periods.periodOn(LocalDate.of(2026, 10, 14));
        server.verify();
    }

    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
