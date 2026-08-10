package com.fantasy.espn.player;

import com.fantasy.espn.config.EspnProperties;
import com.fantasy.espn.player.dto.PlayerStatLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EspnPlayerClientTest {

    /**
     * Shaped like ESPN's real response: a skater whose season total sits among per-game splits
     * and ESPN's own projection, a goalie, and a player who never played.
     */
    private static final String PLAYERS_JSON = """
            [
              {
                "id": 3895074, "fullName": "Connor McDavid", "defaultPositionId": 1,
                "stats": [
                  {"statSourceId": 0, "statSplitTypeId": 5, "scoringPeriodId": 12, "seasonId": 2026,
                   "stats": {"13": 1.0, "25": 22.0}},
                  {"statSourceId": 1, "statSplitTypeId": 0, "scoringPeriodId": 0, "seasonId": 2026,
                   "stats": {"25": 9999.0, "28": 42.0}},
                  {"statSourceId": 0, "statSplitTypeId": 0, "scoringPeriodId": 0, "seasonId": 2026,
                   "stats": {"13": 26.0, "25": 1413.0, "26": 88566.0, "28": 2.0, "30": 67.0}}
                ]
              },
              {
                "id": 3020225, "fullName": "Connor Hellebuyck", "defaultPositionId": 5,
                "stats": [
                  {"statSourceId": 0, "statSplitTypeId": 0, "scoringPeriodId": 0, "seasonId": 2026,
                   "stats": {"1": 47.0, "8": 224482.0, "9": 3.0, "30": 63.0}}
                ]
              },
              {
                "id": 999, "fullName": "Never Played", "defaultPositionId": 4, "stats": []
              }
            ]
            """;

    private MockRestServiceServer server;
    private EspnPlayerClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://espn.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new EspnPlayerClient(builder.build(),
                new EspnProperties("https://espn.test", "fhl", 2026, 2026, null));
    }

    @Test
    void fetchSeasonStats_readsSeasonTotalsForSkatersAndGoalies() {
        server.expect(requestTo(containsString("/apis/v3/games/fhl/seasons/2026/players")))
                .andRespond(withSuccess(PLAYERS_JSON, MediaType.APPLICATION_JSON));

        List<PlayerStatLine> lines = client.fetchSeasonStats(2026);

        assertThat(lines).hasSize(2);
        PlayerStatLine skater = lines.getFirst();
        assertThat(skater.fullName()).isEqualTo("Connor McDavid");
        assertThat(skater.position()).isEqualTo("C");
        assertThat(skater.gamesPlayed()).isEqualTo(67);
        assertThat(skater.hatTricks()).isEqualTo(2);
        assertThat(skater.shifts()).isEqualTo(1413);
        assertThat(skater.timeOnIce()).isEqualTo(88566);
        assertThat(skater.overtimeLosses()).isNull();

        PlayerStatLine goalie = lines.get(1);
        assertThat(goalie.position()).isEqualTo("G");
        assertThat(goalie.overtimeLosses()).isEqualTo(3);
        assertThat(goalie.timeOnIce()).isEqualTo(224482);
        assertThat(goalie.shifts()).isNull();
    }

    @Test
    void fetchSeasonStats_skipsPlayersWithoutASeasonLine() {
        server.expect(requestTo(containsString("/players")))
                .andRespond(withSuccess(PLAYERS_JSON, MediaType.APPLICATION_JSON));

        assertThat(client.fetchSeasonStats(2026))
                .extracting(PlayerStatLine::fullName)
                .doesNotContain("Never Played");
    }

    @Test
    void fetchSeasonStats_ignoresOtherSeasons() {
        server.expect(requestTo(containsString("/players")))
                .andRespond(withSuccess(PLAYERS_JSON, MediaType.APPLICATION_JSON));

        assertThat(client.fetchSeasonStats(2025)).isEmpty();
    }

    @Test
    void fetchSeasonStats_failsLoudlyOnUpstreamError() {
        server.expect(requestTo(containsString("/players")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.fetchSeasonStats(2026))
                .isInstanceOf(IllegalStateException.class);
    }
}
