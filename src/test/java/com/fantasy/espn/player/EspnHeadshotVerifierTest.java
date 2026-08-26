package com.fantasy.espn.player;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EspnHeadshotVerifierTest {

    private static final String CDN = "https://images.test/";

    private MockRestServiceServer server;
    private RestClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        client = builder.build();
    }

    private static FetchedPlayer player(long id, String headshot) {
        return new FetchedPlayer(id, "First", "Last", "First Last", "C", List.of("C"), 9,
                "EDM", headshot, true, false, List.of(), List.of());
    }

    @Test
    void dropsTheHeadshotEspnHasNoPictureBehind() {
        server.expect(requestTo(CDN + "1")).andRespond(withSuccess());
        server.expect(requestTo(CDN + "2")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        List<FetchedPlayer> verified = new EspnHeadshotVerifier(client, true)
                .withVerifiedHeadshots(List.of(player(1, CDN + "1"), player(2, CDN + "2")));

        assertThat(verified).extracting(FetchedPlayer::headshot)
                .containsExactly(CDN + "1", null);
    }

    /** Everything else about the player is untouched — only the picture is in question. */
    @Test
    void keepsTherestOfThePlayerWhenTheHeadshotGoes() {
        server.expect(requestTo(CDN + "2")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        FetchedPlayer verified = new EspnHeadshotVerifier(client, true)
                .withVerifiedHeadshots(List.of(player(2, CDN + "2"))).getFirst();

        assertThat(verified.fullName()).isEqualTo("First Last");
        assertThat(verified.eligiblePositions()).containsExactly("C");
        assertThat(verified.teamAbbrev()).isEqualTo("EDM");
        assertThat(verified.sweaterNumber()).isEqualTo(9);
    }

    /**
     * The check is a courtesy. A CDN that refuses, times out or breaks must not be able to
     * strip the whole pool of its pictures.
     */
    @Test
    void keepsTheHeadshotWhenTheCdnAnswersAnythingButNotFound() {
        server.expect(manyTimes(), requestTo(CDN + "1"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        List<FetchedPlayer> verified = new EspnHeadshotVerifier(client, true)
                .withVerifiedHeadshots(List.of(player(1, CDN + "1")));

        assertThat(verified.getFirst().headshot()).isEqualTo(CDN + "1");
    }

    @Test
    void leavesAPlayerWithNoHeadshotAloneRatherThanAskingAboutNothing() {
        List<FetchedPlayer> verified = new EspnHeadshotVerifier(client, true)
                .withVerifiedHeadshots(List.of(player(1, null)));

        assertThat(verified.getFirst().headshot()).isNull();
        server.verify();
    }

    @Test
    void checksNothingWhenTurnedOff() {
        List<FetchedPlayer> players = List.of(player(1, CDN + "1"));

        assertThat(new EspnHeadshotVerifier(client, false).withVerifiedHeadshots(players))
                .isSameAs(players);
        server.verify();
    }
}
