package com.fantasy.espn.league;

import com.fantasy.espn.config.EspnProperties;
import com.fantasy.espn.credential.EspnCookies;
import com.fantasy.espn.exception.EspnLeagueNotFoundException;
import com.fantasy.espn.exception.EspnPrivateLeagueException;
import com.fantasy.espn.exception.EspnUpstreamException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.function.Function;

/**
 * Thin wrapper over the ESPN Fantasy v3 API. Returns raw Jackson {@link JsonNode} trees —
 * {@link EspnLeagueService} parses them defensively. ESPN's read endpoint is public for public
 * leagues and cookie-authenticated (espn_s2 + SWID) for private ones.
 *
 * The body is fetched as a String and parsed with our own (Jackson 2) ObjectMapper:
 * Spring Boot 4's default converter is Jackson 3, which can't build a Jackson 2 JsonNode.
 */
@Component
public class EspnFantasyClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String gameKey;

    public EspnFantasyClient(RestClient espnApiRestClient, EspnProperties props) {
        this.restClient = espnApiRestClient;
        this.gameKey = props.gameKey();
    }

    /**
     * Without an {@code x-fantasy-filter} ESPN serves one page of 50 players and gives no sign
     * that the rest exist. The status filter is the point here: it asks the league for the players
     * no team in it owns, which is a question only the league-scoped document can answer.
     */
    private static final String PLAYER_FILTER_HEADER = "x-fantasy-filter";
    private static final String AVAILABLE_FILTER = """
            {"players":{"filterStatus":{"value":["FREEAGENT","WAIVERS"]},"limit":%d,\
            "sortPercOwned":{"sortAsc":false,"sortPriority":1}}}""";

    /**
     * The players a league has available: free agents and players on waivers, most owned across
     * ESPN first, which is the closest thing its player document has to "best available".
     */
    public JsonNode getAvailablePlayers(int season, String leagueId, EspnCookies cookies, int limit) {
        return fetch(
                uriBuilder -> uriBuilder
                        .path("/apis/v3/games/{gameKey}/seasons/{season}/segments/0/leagues/{leagueId}/players")
                        .queryParam("view", "kona_player_info")
                        .build(gameKey, season, leagueId),
                cookies,
                AVAILABLE_FILTER.formatted(limit));
    }

    /**
     * A league document for the given season, restricted to the requested {@code views}
     * (e.g. {@code mSettings}, {@code mTeam}). When {@code cookies} is non-null they are sent so
     * ESPN will serve a private league.
     */
    public JsonNode getLeague(int season, String leagueId, EspnCookies cookies, String... views) {
        return fetch(
                uriBuilder -> uriBuilder
                        .path("/apis/v3/games/{gameKey}/seasons/{season}/segments/0/leagues/{leagueId}")
                        .queryParam("view", (Object[]) views)
                        .build(gameKey, season, leagueId),
                cookies,
                null);
    }

    /**
     * One ESPN read: the user's cookies when there are any, an optional player filter, and the
     * status mapping every caller depends on — a private league or stale cookies is the caller's
     * 400, not a fault of ours, and only a failure ESPN itself reported is a 502.
     */
    private JsonNode fetch(
            Function<UriBuilder, URI> uri, EspnCookies cookies, String playerFilter) {
        String body;
        try {
            body = restClient.get()
                    .uri(uri)
                    .headers(headers -> {
                        if (cookies != null) {
                            headers.add(HttpHeaders.COOKIE,
                                    "espn_s2=" + cookies.espnS2() + "; SWID=" + cookies.swid());
                        }
                        if (playerFilter != null) {
                            headers.add(PLAYER_FILTER_HEADER, playerFilter);
                        }
                    })
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) {
                // Private league and the cookies were absent or invalid.
                throw new EspnPrivateLeagueException(
                        "ESPN league is private — valid espn_s2 and SWID cookies are required.");
            }
            if (status == 404) {
                throw new EspnLeagueNotFoundException(
                        "ESPN league not found for the given id and season.");
            }
            throw new EspnUpstreamException("ESPN API call failed (HTTP " + status + ")", e);
        } catch (RestClientException e) {
            throw new EspnUpstreamException("ESPN API call failed: " + e.getMessage(), e);
        }
        if (body == null || body.isBlank()) {
            throw new EspnUpstreamException("ESPN API returned an empty body");
        }
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new EspnUpstreamException("ESPN API returned unparseable JSON", e);
        }
    }
}
