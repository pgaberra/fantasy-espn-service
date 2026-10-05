package com.fantasy.espn.league;

import com.fantasy.espn.config.EspnProperties;
import com.fantasy.espn.credential.EspnCookies;
import com.fantasy.espn.credential.EspnCredentialService;
import com.fantasy.espn.exception.EspnLeagueNotFoundException;
import com.fantasy.espn.league.dto.DraftStatus;
import com.fantasy.espn.league.dto.LeagueRosterPlayer;
import com.fantasy.espn.league.dto.LeagueRostersResponse;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EspnLeagueRostersTest {

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
                        "/seasons/2027/segments/0/leagues/123"
                                + "?view=mRoster&view=mTeam&view=mSettings&view=mDraftDetail")))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Test
    void rosters_listsEachTeamsPlayersAndWhichTeamIsTheUsers() {
        respond("""
                {
                  "settings": {"name": "Beer League"},
                  "draftDetail": {"drafted": true, "inProgress": false},
                  "teams": [
                    {"id": 1, "name": "Alpha", "roster": {"entries": [
                      {"playerId": 111, "lineupSlotId": 0},
                      {"playerId": 222, "lineupSlotId": 8}
                    ]}},
                    {"id": 2, "name": "Bravo", "owners": ["{ccc-333}"], "roster": {"entries": [
                      {"playerId": 333, "lineupSlotId": 7}
                    ]}}
                  ]
                }
                """);

        LeagueRostersResponse rosters = service.rosters("u1", "123");

        assertThat(rosters.leagueId()).isEqualTo("123");
        assertThat(rosters.season()).isEqualTo(2027);
        assertThat(rosters.leagueName()).isEqualTo("Beer League");
        assertThat(rosters.status()).isEqualTo(DraftStatus.FINISHED);
        assertThat(rosters.teams()).extracting("teamId").containsExactly(1, 2);
        assertThat(rosters.teams()).extracting("mine").containsExactly(false, true);
        assertThat(rosters.teams().get(0).playerIds()).containsExactly(111L, 222L);
        assertThat(rosters.teams().get(1).playerIds()).containsExactly(333L);
        server.verify();
    }

    @Test
    void rosters_carriesEachPlayersClubPositionsSlotAndInjury() {
        respond("""
                {
                  "teams": [
                    {"id": 1, "name": "Alpha", "roster": {"entries": [
                      {"playerId": 111, "lineupSlotId": 6, "playerPoolEntry": {"player": {
                        "fullName": "Center Wing", "proTeamId": 6, "jersey": "97", "defaultPositionId": 1,
                        "eligibleSlots": [0, 1, 3, 6, 7, 8], "injuryStatus": "ACTIVE"}}},
                      {"playerId": 222, "lineupSlotId": 8, "playerPoolEntry": {"player": {
                        "fullName": "Hurt Goalie", "proTeamId": 0, "defaultPositionId": 5,
                        "eligibleSlots": [5, 7, 8], "injuryStatus": "OUT"}}},
                      {"playerId": 333}
                    ]}}
                  ]
                }
                """);

        List<LeagueRosterPlayer> players = service.rosters("u1", "123").teams().getFirst().players();

        assertThat(players).extracting("espnId").containsExactly(111L, 222L, 333L);
        LeagueRosterPlayer skater = players.get(0);
        assertThat(skater.fullName()).isEqualTo("Center Wing");
        assertThat(skater.teamAbbrev()).isEqualTo("EDM");
        assertThat(skater.uniformNumber()).isEqualTo(97);
        assertThat(skater.goalie()).isFalse();
        assertThat(skater.eligiblePositions()).containsExactly("C", "LW");
        assertThat(skater.lineupSlot()).isEqualTo("Util");
        assertThat(skater.injuryStatus()).isNull();
        LeagueRosterPlayer goalie = players.get(1);
        assertThat(goalie.goalie()).isTrue();
        assertThat(goalie.teamAbbrev()).isNull();
        assertThat(goalie.lineupSlot()).isEqualTo("IR");
        assertThat(goalie.injuryStatus()).isEqualTo("OUT");
        LeagueRosterPlayer bare = players.get(2);
        assertThat(bare.fullName()).isNull();
        assertThat(bare.eligiblePositions()).isEmpty();
        assertThat(bare.lineupSlot()).isNull();
    }

    @Test
    void rosters_leavesATeamThatHasNotDraftedEmptyAndSkipsEmptySlots() {
        respond("""
                {
                  "draftDetail": {"drafted": false, "inProgress": false},
                  "teams": [
                    {"id": 1, "location": "Beta", "nickname": "Squad"},
                    {"id": 2, "roster": {"entries": [{"lineupSlotId": 0}, {"playerId": 444}, {"playerId": 444}]}}
                  ]
                }
                """);

        LeagueRostersResponse rosters = service.rosters("u1", "123");

        assertThat(rosters.status()).isEqualTo(DraftStatus.PRE_DRAFT);
        assertThat(rosters.leagueName()).isNull();
        assertThat(rosters.teams()).extracting("name").containsExactly("Beta Squad", "Team 2");
        assertThat(rosters.teams()).extracting("playerIds").containsExactly(List.of(), List.of(444L));
    }

    @Test
    void rosters_neverFallsBackToLastSeasonsLeague() {
        server.expect(requestTo(containsString("/seasons/2027/")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.rosters("u1", "123"))
                .isInstanceOf(EspnLeagueNotFoundException.class);
        server.verify();
    }

    @Test
    void rosters_rejectsANonNumericLeagueId() {
        assertThatThrownBy(() -> service.rosters("u1", "12a"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
