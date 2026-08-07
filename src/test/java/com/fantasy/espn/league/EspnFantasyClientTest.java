package com.fantasy.espn.league;

import com.fantasy.espn.config.EspnProperties;
import com.fantasy.espn.credential.EspnCookies;
import com.fantasy.espn.exception.EspnLeagueNotFoundException;
import com.fantasy.espn.exception.EspnPrivateLeagueException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EspnFantasyClientTest {

    private MockRestServiceServer server;
    private EspnFantasyClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://espn.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new EspnFantasyClient(builder.build(), new EspnProperties("https://espn.test", "fhl", 2026, null));
    }

    @Test
    void getLeague_buildsHockeyUrlWithViews() {
        server.expect(requestTo(containsString(
                        "/apis/v3/games/fhl/seasons/2025/segments/0/leagues/123?view=mSettings")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.getLeague(2025, "123", null, "mSettings");

        server.verify();
    }

    @Test
    void getLeague_sendsCookiesForPrivateLeague() {
        server.expect(requestTo(containsString("/leagues/123")))
                .andExpect(header("Cookie", containsString("espn_s2=s2val")))
                .andExpect(header("Cookie", containsString("SWID={ID-1}")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.getLeague(2025, "123", new EspnCookies("s2val", "{ID-1}"), "mSettings");

        server.verify();
    }

    @Test
    void getLeague_maps401ToPrivateLeague() {
        server.expect(requestTo(containsString("/leagues/123")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.getLeague(2025, "123", null, "mSettings"))
                .isInstanceOf(EspnPrivateLeagueException.class);
    }

    @Test
    void getLeague_maps404ToNotFound() {
        server.expect(requestTo(containsString("/leagues/999")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.getLeague(2025, "999", null, "mSettings"))
                .isInstanceOf(EspnLeagueNotFoundException.class);
    }

    @Test
    void getLeague_maps500ToUpstreamFailure() {
        server.expect(requestTo(containsString("/leagues/123")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.getLeague(2025, "123", null, "mSettings"))
                .isInstanceOf(IllegalStateException.class);
    }
}
