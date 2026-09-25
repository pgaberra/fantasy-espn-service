package com.fantasy.espn.league;

import com.fantasy.espn.config.EspnProperties;
import com.fantasy.espn.credential.EspnCookies;
import com.fantasy.espn.credential.EspnCredentialService;
import com.fantasy.espn.exception.EspnLeagueNotFoundException;
import com.fantasy.espn.league.dto.DraftStatus;
import com.fantasy.espn.league.dto.LeagueDraftPick;
import com.fantasy.espn.league.dto.LeagueDraftResponse;
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

class EspnLeagueDraftTest {

    private static final String TEAMS = """
            "teams": [
              {"id": 1, "name": "Alpha"},
              {"id": 2, "name": "Bravo", "owners": ["{ccc-333}"]},
              {"id": 3, "name": "Charlie"}
            ]
            """;

    private MockRestServiceServer server;
    private EspnCredentialService credentialService;
    private EspnLeagueService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://espn.test");
        server = MockRestServiceServer.bindTo(builder).build();
        EspnProperties props = new EspnProperties("https://espn.test", "fhl", 2027, 2026, 2025, null);
        credentialService = mock(EspnCredentialService.class);
        service = new EspnLeagueService(new EspnFantasyClient(builder.build(), props), credentialService, props);
        when(credentialService.find("u1")).thenReturn(Optional.of(new EspnCookies("s2", "{CCC-333}")));
    }

    private void respond(String json) {
        server.expect(requestTo(containsString(
                        "/seasons/2027/segments/0/leagues/123?view=mDraftDetail&view=mTeam&view=mSettings")))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Test
    void draft_listsThePicksMadeInOrderWithTheTeamsInPickOrder() {
        respond("""
                {
                  "draftDetail": {"drafted": false, "inProgress": true, "picks": [
                    {"overallPickNumber": 2, "roundId": 1, "roundPickNumber": 2, "teamId": 1, "playerId": 222},
                    {"overallPickNumber": 1, "roundId": 1, "roundPickNumber": 1, "teamId": 3, "playerId": 111,
                     "keeper": true},
                    {"overallPickNumber": 3, "roundId": 1, "roundPickNumber": 3, "teamId": 2, "playerId": -1}
                  ]},
                  "settings": {"draftSettings": {"type": "SNAKE", "pickOrder": [3, 1, 2]}},
                  %s
                }
                """.formatted(TEAMS));

        LeagueDraftResponse draft = service.draft("u1", "123");

        assertThat(draft.status()).isEqualTo(DraftStatus.IN_PROGRESS);
        assertThat(draft.season()).isEqualTo(2027);
        assertThat(draft.auction()).isFalse();
        assertThat(draft.orderKnown()).isTrue();
        assertThat(draft.teams()).extracting("teamId").containsExactly(3, 1, 2);
        assertThat(draft.teams()).extracting("mine").containsExactly(false, false, true);
        // An entry with no player yet is not a pick.
        assertThat(draft.picks()).containsExactly(
                new LeagueDraftPick(1, 1, 3, 111, true),
                new LeagueDraftPick(2, 1, 1, 222, false));
        server.verify();
    }

    @Test
    void draft_derivesTheOverallNumberFromTheRoundWhenEspnLeavesItOut() {
        respond("""
                {
                  "draftDetail": {"inProgress": true, "picks": [
                    {"roundId": 2, "roundPickNumber": 1, "teamId": 2, "playerId": 444}
                  ]},
                  "settings": {"draftSettings": {"pickOrder": [3, 1, 2]}},
                  %s
                }
                """.formatted(TEAMS));

        assertThat(service.draft("u1", "123").picks())
                .containsExactly(new LeagueDraftPick(4, 2, 2, 444, false));
    }

    @Test
    void draft_takesTheOrderFromTheFirstRoundWhenThePickOrderDoesNotPlaceEveryTeam() {
        respond("""
                {
                  "draftDetail": {"inProgress": true, "picks": [
                    {"overallPickNumber": 1, "roundId": 1, "teamId": 2, "playerId": 1},
                    {"overallPickNumber": 2, "roundId": 1, "teamId": 3, "playerId": 2},
                    {"overallPickNumber": 3, "roundId": 1, "teamId": 1, "playerId": 3}
                  ]},
                  "settings": {"draftSettings": {"pickOrder": [1]}},
                  %s
                }
                """.formatted(TEAMS));

        LeagueDraftResponse draft = service.draft("u1", "123");

        assertThat(draft.orderKnown()).isTrue();
        assertThat(draft.teams()).extracting("teamId").containsExactly(2, 3, 1);
    }

    @Test
    void draft_saysTheOrderIsUnknownBeforeEspnHasSetIt() {
        respond("""
                {
                  "draftDetail": {"drafted": false, "inProgress": false},
                  "settings": {"draftSettings": {"type": "SNAKE", "pickOrder": []}},
                  %s
                }
                """.formatted(TEAMS));

        LeagueDraftResponse draft = service.draft("u1", "123");

        assertThat(draft.status()).isEqualTo(DraftStatus.PRE_DRAFT);
        assertThat(draft.orderKnown()).isFalse();
        assertThat(draft.teams()).extracting("teamId").containsExactly(1, 2, 3);
        assertThat(draft.picks()).isEmpty();
    }

    @Test
    void draft_reportsAFinishedAuction() {
        respond("""
                {
                  "draftDetail": {"drafted": true, "inProgress": false, "picks": []},
                  "settings": {"draftSettings": {"type": "AUCTION", "pickOrder": [1, 2, 3]}},
                  %s
                }
                """.formatted(TEAMS));

        LeagueDraftResponse draft = service.draft("u1", "123");

        assertThat(draft.status()).isEqualTo(DraftStatus.FINISHED);
        assertThat(draft.auction()).isTrue();
    }

    @Test
    void draft_isUnknownWithoutADraftDetail() {
        respond("{%s}".formatted(TEAMS));

        assertThat(service.draft("u1", "123").status()).isEqualTo(DraftStatus.UNKNOWN);
    }

    @Test
    void draft_neverFallsBackToLastSeasonsDraft() {
        server.expect(requestTo(containsString("/seasons/2027/")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.draft("u1", "123"))
                .isInstanceOf(EspnLeagueNotFoundException.class);
        server.verify();
    }

    @Test
    void draft_rejectsANonNumericLeagueId() {
        assertThatThrownBy(() -> service.draft("u1", "12a"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
