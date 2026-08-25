package com.fantasy.espn.league;

import com.fantasy.espn.config.EspnProperties;
import com.fantasy.espn.credential.EspnCookies;
import com.fantasy.espn.credential.EspnCredentialService;
import com.fantasy.espn.exception.EspnLeagueNotFoundException;
import com.fantasy.espn.league.dto.LeagueSettingsResponse;
import com.fantasy.espn.league.dto.LeagueTeamsResponse;
import com.fantasy.espn.league.dto.RosterSlot;
import com.fantasy.espn.league.dto.StatCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EspnLeagueServiceTest {

    private static final String SETTINGS_JSON = """
            {
              "id": 123,
              "settings": {
                "name": "Test League",
                "size": 12,
                "scoringSettings": {
                  "scoringType": "H2H_POINTS",
                  "scoringItems": [
                    {"statId": 13, "points": 6.0},
                    {"statId": 14, "points": 4.0},
                    {"statId": 6, "points": 0.2},
                    {"statId": 999, "points": 1.0}
                  ]
                },
                "rosterSettings": {
                  "lineupSlotCounts": {
                    "0": 2, "1": 2, "2": 2, "4": 4, "5": 2, "6": 1, "7": 4, "8": 1, "3": 0
                  }
                }
              }
            }
            """;

    private static final String TEAMS_JSON = """
            {
              "teams": [
                {"id": 1, "name": "Alpha", "owners": ["{AAA-111}"]},
                {"id": 2, "location": "Beta", "nickname": "Squad", "owners": ["{BBB-222}"]}
              ]
            }
            """;

    private static final int CONFIGURED_SEASON = 2026;

    private final EspnProperties props =
            new EspnProperties("https://espn.test", "fhl", CONFIGURED_SEASON, 2026, 2025, null);

    private MockRestServiceServer server;
    private EspnCredentialService credentialService;
    private EspnLeagueService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://espn.test");
        server = MockRestServiceServer.bindTo(builder).build();
        EspnFantasyClient client =
                new EspnFantasyClient(builder.build(), props);
        credentialService = mock(EspnCredentialService.class);
        service = new EspnLeagueService(client, credentialService, props);
    }

    @Test
    void settings_mapsScoringAndRoster() {
        when(credentialService.find("u1")).thenReturn(Optional.empty());
        server.expect(requestTo(containsString(
                        "/apis/v3/games/fhl/seasons/2025/segments/0/leagues/123?view=mSettings")))
                .andRespond(withSuccess(SETTINGS_JSON, MediaType.APPLICATION_JSON));

        LeagueSettingsResponse settings = service.settings("u1", 2025, "123");

        assertThat(settings.leagueId()).isEqualTo("123");
        assertThat(settings.name()).isEqualTo("Test League");
        assertThat(settings.scoringType()).isEqualTo("H2H_POINTS");
        assertThat(settings.size()).isEqualTo(12);
        assertThat(settings.statCategories()).containsExactlyInAnyOrder(
                new StatCategory(13, "G", 6.0),
                new StatCategory(14, "A", 4.0),
                new StatCategory(6, "SV", 0.2),
                new StatCategory(999, "STAT_999", 1.0));
        // slot "3" (F) has count 0 and is dropped.
        assertThat(settings.rosterPositions()).containsExactlyInAnyOrder(
                new RosterSlot("C", 2),
                new RosterSlot("LW", 2),
                new RosterSlot("RW", 2),
                new RosterSlot("D", 4),
                new RosterSlot("G", 2),
                new RosterSlot("Util", 1),
                new RosterSlot("BN", 4),
                new RosterSlot("IR", 1));
        server.verify();
    }

    @Test
    void teams_marksTheUsersTeamViaSwid() {
        when(credentialService.find("u1")).thenReturn(Optional.of(new EspnCookies("s2", "{bbb-222}")));
        server.expect(requestTo(containsString(
                        "/apis/v3/games/fhl/seasons/2025/segments/0/leagues/123?view=mTeam")))
                .andRespond(withSuccess(TEAMS_JSON, MediaType.APPLICATION_JSON));

        LeagueTeamsResponse teams = service.teams("u1", 2025, "123");

        assertThat(teams.teams()).hasSize(2);
        assertThat(teams.teams().get(0).name()).isEqualTo("Alpha");
        assertThat(teams.teams().get(0).mine()).isFalse();
        // "Beta" + "Squad"; owner {BBB-222} matches the user's SWID (case-insensitive, braces stripped).
        assertThat(teams.teams().get(1).name()).isEqualTo("Beta Squad");
        assertThat(teams.teams().get(1).mine()).isTrue();
        server.verify();
    }

    @Test
    void settings_usesTheConfiguredSeasonWhenNoneIsGiven() {
        when(credentialService.find("u1")).thenReturn(Optional.empty());
        server.expect(requestTo(containsString(
                        "/apis/v3/games/fhl/seasons/" + CONFIGURED_SEASON + "/segments/0/leagues/123")))
                .andRespond(withSuccess(SETTINGS_JSON, MediaType.APPLICATION_JSON));

        assertThat(service.settings("u1", null, "123").leagueId()).isEqualTo("123");

        server.verify();
    }

    @Test
    void settings_fallsBackToThePreviousSeasonWhenTheLeagueIsMissing() {
        when(credentialService.find("u1")).thenReturn(Optional.empty());
        // The user hasn't renewed for the configured season yet — ESPN 404s there, so the
        // reader retries the season before it rather than failing the sync.
        server.expect(requestTo(containsString("/seasons/" + CONFIGURED_SEASON + "/")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(containsString("/seasons/" + (CONFIGURED_SEASON - 1) + "/")))
                .andRespond(withSuccess(SETTINGS_JSON, MediaType.APPLICATION_JSON));

        assertThat(service.settings("u1", null, "123").name()).isEqualTo("Test League");

        server.verify();
    }

    @Test
    void settings_doesNotFallBackWhenAnExplicitSeasonIsGiven() {
        when(credentialService.find("u1")).thenReturn(Optional.empty());
        server.expect(requestTo(containsString("/seasons/2025/")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.settings("u1", 2025, "123"))
                .isInstanceOf(EspnLeagueNotFoundException.class);

        server.verify();
    }

    @Test
    void settings_rejectsNonNumericLeagueId() {
        assertThatThrownBy(() -> service.settings("u1", 2025, "abc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void settings_rejectsImplausibleSeason() {
        assertThatThrownBy(() -> service.settings("u1", 1800, "123"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
