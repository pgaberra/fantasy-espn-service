package com.fantasy.espn.player;

import com.fantasy.espn.config.EspnProperties;
import com.fantasy.espn.exception.EspnUpstreamException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EspnPlayerClientTest {

    /**
     * Shaped like ESPN's real response: a skater whose season totals sit among per-game splits,
     * ESPN's own projection and an empty row for the season not yet played; a dual-eligible
     * winger; a goalie; a player with no stats at all; and a record with no position.
     */
    private static final String PLAYERS_JSON = """
            [
              {
                "id": 3895074, "fullName": "Connor McDavid", "firstName": "Connor",
                "lastName": "McDavid", "defaultPositionId": 1, "jersey": "97", "proTeamId": 6,
                "active": true, "eligibleSlots": [3, 0, 6, 7, 8],
                "stats": [
                  {"statSourceId": 0, "statSplitTypeId": 5, "scoringPeriodId": 12, "seasonId": 2026,
                   "stats": {"13": 1.0, "30": 1.0}},
                  {"statSourceId": 1, "statSplitTypeId": 0, "scoringPeriodId": 0, "seasonId": 2027,
                   "stats": {"13": 55.0, "30": 82.0}},
                  {"statSourceId": 0, "statSplitTypeId": 0, "scoringPeriodId": 0, "seasonId": 2027,
                   "stats": {}},
                  {"statSourceId": 0, "statSplitTypeId": 0, "scoringPeriodId": 0, "seasonId": 2026,
                   "stats": {"13": 48.0, "14": 90.0, "15": 17.0, "16": 138.0, "17": 44.0,
                             "18": 13.0, "20": 1.0, "22": 4.0, "23": 457.0, "24": 466.0,
                             "25": 1841.0, "26": 113127.0, "27": 1379.0, "28": 3.0, "29": 300.0,
                             "30": 82.0, "31": 40.0, "32": 30.0, "38": 54.0, "39": 2.0}}
                ]
              },
              {
                "id": 4233563, "fullName": "Drake Batherson", "firstName": "Drake",
                "lastName": "Batherson", "defaultPositionId": 3, "jersey": "19", "proTeamId": 14,
                "active": true, "eligibleSlots": [2, 1, 3, 6, 7, 8], "stats": []
              },
              {
                "id": 3020225, "fullName": "Andrei Vasilevskiy", "firstName": "Andrei",
                "lastName": "Vasilevskiy", "defaultPositionId": 5, "proTeamId": 20,
                "active": true, "eligibleSlots": [5, 7, 8],
                "stats": [
                  {"statSourceId": 0, "statSplitTypeId": 0, "scoringPeriodId": 0, "seasonId": 2026,
                   "stats": {"0": 58.0, "1": 39.0, "2": 15.0, "3": 1483.0, "4": 132.0, "6": 1353.0,
                             "7": 2.0, "8": 205845.0, "9": 4.0, "10": 2.3085, "11": 0.91234,
                             "30": 58.0}}
                ]
              },
              {
                "id": 999, "fullName": "No Position", "defaultPositionId": 99, "stats": []
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
                new EspnProperties("https://espn.test", "fhl", 2027, 2026, 2025, null));
    }

    /**
     * Without the filter header ESPN serves one page of 50 players and gives no sign that the
     * rest exist, so the cache silently ends up holding a fraction of the league.
     */
    @Test
    void fetchPlayers_asksForEveryPlayerRatherThanTheFirstPage() {
        server.expect(requestTo(containsString("/players")))
                .andExpect(header("x-fantasy-filter", containsString("\"limit\"")))
                .andRespond(withSuccess(PLAYERS_JSON, MediaType.APPLICATION_JSON));

        client.fetchPlayers(2026);

        server.verify();
    }

    /** ESPN keys a season by the year it ends in; the service asks in start years. */
    @Test
    void fetchPlayers_asksEspnForTheSeasonThatEndsAYearLater() {
        server.expect(requestTo(containsString("/apis/v3/games/fhl/seasons/2027/players")))
                .andRespond(withSuccess(PLAYERS_JSON, MediaType.APPLICATION_JSON));

        client.fetchPlayers(2026);

        server.verify();
    }

    @Test
    void fetchPlayers_readsIdentityAndEligibility() {
        server.expect(requestTo(containsString("/players")))
                .andRespond(withSuccess(PLAYERS_JSON, MediaType.APPLICATION_JSON));

        FetchedPlayer skater = client.fetchPlayers(2026).getFirst();

        assertThat(skater.fullName()).isEqualTo("Connor McDavid");
        assertThat(skater.firstName()).isEqualTo("Connor");
        assertThat(skater.lastName()).isEqualTo("McDavid");
        assertThat(skater.position()).isEqualTo("C");
        assertThat(skater.sweaterNumber()).isEqualTo(97);
        assertThat(skater.teamAbbrev()).isEqualTo("EDM");
        assertThat(skater.active()).isTrue();
        assertThat(skater.goalie()).isFalse();
        assertThat(skater.headshot()).contains("/3895074.png");
        // The roster-only slots (F, Util, BN, IR) are not eligibility.
        assertThat(skater.eligiblePositions()).containsExactly("C");
    }

    @Test
    void fetchPlayers_ordersEligiblePositionsTheSameWayEverySync() {
        server.expect(requestTo(containsString("/players")))
                .andRespond(withSuccess(PLAYERS_JSON, MediaType.APPLICATION_JSON));

        // ESPN lists this winger's slots as [RW, LW, F, ...]; the read model always says LW,RW.
        assertThat(client.fetchPlayers(2026).get(1).eligiblePositions())
                .containsExactly("LW", "RW");
    }

    @Test
    void fetchPlayers_readsSeasonTotalsAndDerivesWhatEspnDoesNotReport() {
        server.expect(requestTo(containsString("/players")))
                .andRespond(withSuccess(PLAYERS_JSON, MediaType.APPLICATION_JSON));

        FetchedSkaterSeason line = client.fetchPlayers(2026).getFirst().skaterSeasons().getFirst();

        // ESPN's 2026 is the 2025-26 season, which the read model calls 2025.
        assertThat(line.season()).isEqualTo(2025);
        assertThat(line.gamesPlayed()).isEqualTo(82);
        assertThat(line.goals()).isEqualTo(48);
        assertThat(line.assists()).isEqualTo(90);
        assertThat(line.points()).isEqualTo(138);
        assertThat(line.plusMinus()).isEqualTo(17);
        assertThat(line.pim()).isEqualTo(44);
        assertThat(line.powerPlayGoals()).isEqualTo(13);
        assertThat(line.powerPlayPoints()).isEqualTo(54);
        assertThat(line.shorthandedGoals()).isEqualTo(1);
        assertThat(line.shorthandedPoints()).isEqualTo(2);
        assertThat(line.gameWinningGoals()).isEqualTo(4);
        assertThat(line.shots()).isEqualTo(300);
        assertThat(line.hits()).isEqualTo(40);
        assertThat(line.blockedShots()).isEqualTo(30);
        assertThat(line.totalFaceoffWins()).isEqualTo(457);
        assertThat(line.totalFaceoffLosses()).isEqualTo(466);
        assertThat(line.hatTricks()).isEqualTo(3);
        assertThat(line.shifts()).isEqualTo(1841);
        assertThat(line.timeOnIce()).isEqualTo(113127);
        // ESPN reports neither of these; they come from the counts it does report.
        assertThat(line.shootingPctg()).isEqualTo(0.16);
        assertThat(line.faceoffWinningPctg()).isCloseTo(0.495, within(0.001));
        // ESPN reports average ice time in seconds; the read model carries Yahoo's "MM:SS".
        assertThat(line.avgToi()).isEqualTo("22:59");
    }

    @Test
    void fetchPlayers_readsGoalieTotals() {
        server.expect(requestTo(containsString("/players")))
                .andRespond(withSuccess(PLAYERS_JSON, MediaType.APPLICATION_JSON));

        FetchedPlayer goalie = client.fetchPlayers(2026).get(2);

        assertThat(goalie.goalie()).isTrue();
        assertThat(goalie.position()).isEqualTo("G");
        assertThat(goalie.eligiblePositions()).containsExactly("G");
        // ESPN omits the jersey for some players; it must not fail the parse.
        assertThat(goalie.sweaterNumber()).isNull();
        assertThat(goalie.skaterSeasons()).isEmpty();
        FetchedGoalieSeason line = goalie.goalieSeasons().getFirst();
        assertThat(line.season()).isEqualTo(2025);
        assertThat(line.gamesPlayed()).isEqualTo(58);
        assertThat(line.gamesStarted()).isEqualTo(58);
        assertThat(line.wins()).isEqualTo(39);
        assertThat(line.losses()).isEqualTo(15);
        assertThat(line.shutouts()).isEqualTo(2);
        assertThat(line.shotsAgainst()).isEqualTo(1483);
        assertThat(line.saves()).isEqualTo(1353);
        assertThat(line.goalsAgainst()).isEqualTo(132);
        assertThat(line.goalsAgainstAvg()).isCloseTo(2.31, within(0.01));
        assertThat(line.savePctg()).isCloseTo(0.912, within(0.001));
        assertThat(line.overtimeLosses()).isEqualTo(4);
        assertThat(line.timeOnIce()).isEqualTo(205845);
    }

    /**
     * ESPN opens a season months before it is played and answers for it with an empty stat
     * object. That is not a scoreless season, so it is not a season line at all.
     */
    @Test
    void fetchPlayers_ignoresSplitsProjectionsAndTheSeasonNotYetPlayed() {
        server.expect(requestTo(containsString("/players")))
                .andRespond(withSuccess(PLAYERS_JSON, MediaType.APPLICATION_JSON));

        assertThat(client.fetchPlayers(2026).getFirst().skaterSeasons())
                .extracting(FetchedSkaterSeason::season)
                .containsExactly(2025);
    }

    /** A player with no stats is still in the pool — that is a rookie, not a missing player. */
    @Test
    void fetchPlayers_keepsAPlayerWithNoStatsAtAll() {
        server.expect(requestTo(containsString("/players")))
                .andRespond(withSuccess(PLAYERS_JSON, MediaType.APPLICATION_JSON));

        FetchedPlayer rookie = client.fetchPlayers(2026).get(1);

        assertThat(rookie.fullName()).isEqualTo("Drake Batherson");
        assertThat(rookie.skaterSeasons()).isEmpty();
        assertThat(rookie.goalieSeasons()).isEmpty();
    }

    @Test
    void fetchPlayers_skipsRecordsWithNoPositionToPlaceThemIn() {
        server.expect(requestTo(containsString("/players")))
                .andRespond(withSuccess(PLAYERS_JSON, MediaType.APPLICATION_JSON));

        assertThat(client.fetchPlayers(2026))
                .extracting(FetchedPlayer::fullName)
                .doesNotContain("No Position");
    }

    @Test
    void fetchPlayers_failsLoudlyOnUpstreamError() {
        server.expect(requestTo(containsString("/players")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.fetchPlayers(2026))
                .isInstanceOf(EspnUpstreamException.class);
    }

    @Test
    void fetchPlayers_returnsEveryPlayerItCouldPlace() {
        server.expect(requestTo(containsString("/players")))
                .andRespond(withSuccess(PLAYERS_JSON, MediaType.APPLICATION_JSON));

        assertThat(client.fetchPlayers(2026)).hasSize(3);
    }

    /** ESPN's own abbreviations are the ones the app shows, so they are passed through. */
    @Test
    void fetchPlayers_mapsProTeamIdToTheAbbreviationTheAppUses() {
        server.expect(requestTo(containsString("/players")))
                .andRespond(withSuccess(PLAYERS_JSON, MediaType.APPLICATION_JSON));

        List<FetchedPlayer> players = client.fetchPlayers(2026);

        assertThat(players.get(1).teamAbbrev()).isEqualTo("OTT");
        assertThat(players.get(2).teamAbbrev()).isEqualTo("TB");
    }
}
