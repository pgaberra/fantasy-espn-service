package com.fantasy.espn.league;

import com.fantasy.espn.config.EspnProperties;
import com.fantasy.espn.credential.EspnCookies;
import com.fantasy.espn.credential.EspnCredentialService;
import com.fantasy.espn.exception.EspnPrivateLeagueException;
import com.fantasy.espn.league.dto.AvailablePlayer;
import com.fantasy.espn.league.dto.EspnAvailability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EspnFreeAgentsTest {

    private static final String AVAILABLE_JSON = """
            {
              "players": [
                {
                  "status": "FREEAGENT",
                  "player": {
                    "id": 4565,
                    "fullName": "Spencer Knight",
                    "defaultPositionId": 5,
                    "proTeamId": 7,
                    "jersey": "30",
                    "eligibleSlots": [5, 7, 8]
                  }
                },
                {
                  "status": "WAIVERS",
                  "player": {
                    "id": 3900,
                    "fullName": "Jack Roslovic",
                    "defaultPositionId": 1,
                    "proTeamId": 20,
                    "eligibleSlots": [0, 2, 6, 7]
                  }
                },
                {
                  "player": {
                    "id": 4100,
                    "fullName": "Status Unknown",
                    "defaultPositionId": 4,
                    "proTeamId": 0,
                    "eligibleSlots": [4]
                  }
                },
                {
                  "status": "FREEAGENT",
                  "player": {"id": 1, "fullName": "No Position"}
                }
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
        credentialService = mock(EspnCredentialService.class);
        service = new EspnLeagueService(new EspnFantasyClient(builder.build(), props),
                credentialService, props);
    }

    @Test
    void asksTheLeagueDocumentWithTheStatusFilterAndTheUsersCookies() {
        when(credentialService.find("u1")).thenReturn(Optional.of(new EspnCookies("s2val", "{SWID}")));
        server.expect(requestTo(containsString(
                        "/apis/v3/games/fhl/seasons/2026/segments/0/leagues/123/players?view=kona_player_info")))
                .andExpect(header("Cookie", containsString("espn_s2=s2val")))
                .andExpect(header("x-fantasy-filter", containsString("\"FREEAGENT\",\"WAIVERS\"")))
                .andExpect(header("x-fantasy-filter", containsString("\"limit\":150")))
                .andRespond(withSuccess(AVAILABLE_JSON, MediaType.APPLICATION_JSON));

        service.freeAgents("u1", null, "123", 150);

        server.verify();
    }

    @Test
    void mapsIdentityPositionsAndAvailability() {
        when(credentialService.find("u1")).thenReturn(Optional.empty());
        server.expect(requestTo(containsString("/leagues/123/players")))
                .andRespond(withSuccess(AVAILABLE_JSON, MediaType.APPLICATION_JSON));

        List<AvailablePlayer> available = service.freeAgents("u1", null, "123", 150);

        // The player with no position is dropped: there is nothing to start him at.
        assertThat(available).hasSize(3);
        AvailablePlayer knight = available.getFirst();
        assertThat(knight.espnId()).isEqualTo(4565L);
        assertThat(knight.fullName()).isEqualTo("Spencer Knight");
        assertThat(knight.position()).isEqualTo("G");
        assertThat(knight.goalie()).isTrue();
        assertThat(knight.teamAbbrev()).isEqualTo("CAR");
        assertThat(knight.uniformNumber()).isEqualTo(30);
        // Slots 7 (BN) and 8 (IR) are roster slots, not eligibility.
        assertThat(knight.eligiblePositions()).containsExactly("G");
        assertThat(knight.availability()).isEqualTo(EspnAvailability.FREE_AGENT);

        AvailablePlayer roslovic = available.get(1);
        assertThat(roslovic.availability()).isEqualTo(EspnAvailability.WAIVERS);
        assertThat(roslovic.eligiblePositions()).containsExactly("C", "RW");
        assertThat(roslovic.teamAbbrev()).isEqualTo("TB");
        assertThat(roslovic.uniformNumber()).isNull();

        AvailablePlayer unknown = available.get(2);
        assertThat(unknown.availability()).isEqualTo(EspnAvailability.UNKNOWN);
        // ESPN's proTeamId 0 is its free-agent bucket, not a club.
        assertThat(unknown.teamAbbrev()).isNull();
    }

    @Test
    void holdsTheLimitEvenWhenEspnServesMore() {
        when(credentialService.find("u1")).thenReturn(Optional.empty());
        server.expect(requestTo(containsString("/leagues/123/players")))
                .andRespond(withSuccess(AVAILABLE_JSON, MediaType.APPLICATION_JSON));

        assertThat(service.freeAgents("u1", null, "123", 2)).hasSize(2);
    }

    @Test
    void fallsBackToTheSeasonBeforeWhenEspnHasNoLeagueForThisOne() {
        when(credentialService.find("u1")).thenReturn(Optional.empty());
        server.expect(requestTo(containsString("/seasons/2026/")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(containsString("/seasons/2025/")))
                .andRespond(withSuccess(AVAILABLE_JSON, MediaType.APPLICATION_JSON));

        assertThat(service.freeAgents("u1", null, "123", 150)).isNotEmpty();
        server.verify();
    }

    @Test
    void aLeagueEspnRefusesIsTheCallersProblemRatherThanOurs() {
        when(credentialService.find("u1")).thenReturn(Optional.empty());
        server.expect(requestTo(containsString("/leagues/123/players")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> service.freeAgents("u1", 2026, "123", 150))
                .isInstanceOf(EspnPrivateLeagueException.class);
    }

    @Test
    void aMalformedLeagueIdIsRefusedBeforeEspnIsAsked() {
        assertThatThrownBy(() -> service.freeAgents("u1", null, "not-a-league", 150))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
