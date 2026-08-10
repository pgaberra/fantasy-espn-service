package com.fantasy.espn.player;

import com.fantasy.espn.config.EspnProperties;
import com.fantasy.espn.player.dto.PlayerStatLine;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads ESPN's season-long player stat lines. Unlike the league endpoints this one is fully
 * public — it needs no league id and no cookies — which is what makes it usable as a stat
 * source for every user rather than only for the one whose league we are reading.
 *
 * <p>The response is tens of megabytes (every player carries a per-game split for the whole
 * season), so it is parsed as a stream: one player object is materialised at a time and only
 * the season-total entry is kept.
 */
@Component
public class EspnPlayerClient {

    /** ESPN hockey (fhl) stat ids for the stats Yahoo does not report. */
    private static final int GAMES_PLAYED = 30;
    private static final int SHIFTS = 25;
    private static final int HAT_TRICKS = 28;
    private static final int OVERTIME_LOSSES = 9;
    private static final int SKATER_TIME_ON_ICE = 26;
    private static final int GOALIE_TIME_ON_ICE = 8;

    /** ESPN's defaultPositionId. */
    private static final Map<Integer, String> POSITION_CODE =
            Map.of(1, "C", 2, "LW", 3, "RW", 4, "D", 5, "G");

    /**
     * Without this header ESPN serves one page of 50 players and gives no sign that the rest
     * exist — no total, no next link, just a short array. The limit itself is not honoured (any
     * value returns the whole universe); it is the presence of the filter that lifts the page.
     */
    private static final String PLAYER_FILTER_HEADER = "x-fantasy-filter";
    private static final String ALL_PLAYERS_FILTER = "{\"players\":{\"limit\":5000}}";

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String gameKey;

    public EspnPlayerClient(RestClient espnApiRestClient, EspnProperties props) {
        this.restClient = espnApiRestClient;
        this.gameKey = props.gameKey();
    }

    public List<PlayerStatLine> fetchSeasonStats(int season) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/apis/v3/games/{gameKey}/seasons/{season}/players")
                            .queryParam("scoringPeriodId", 0)
                            .queryParam("view", "kona_player_info")
                            .build(gameKey, season))
                    .header(PLAYER_FILTER_HEADER, ALL_PLAYERS_FILTER)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status < 200 || status >= 300) {
                            throw new IllegalStateException(
                                    "ESPN player stats call failed (HTTP " + status + ")");
                        }
                        return parse(response.getBody(), season);
                    });
        } catch (RestClientException e) {
            throw new IllegalStateException("ESPN player stats call failed: " + e.getMessage(), e);
        }
    }

    private List<PlayerStatLine> parse(InputStream body, int season) throws IOException {
        List<PlayerStatLine> lines = new ArrayList<>();
        try (JsonParser parser = objectMapper.getFactory().createParser(body)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IllegalStateException("ESPN player stats response was not a JSON array");
            }
            while (parser.nextToken() == JsonToken.START_OBJECT) {
                JsonNode player = parser.readValueAsTree();
                PlayerStatLine line = toStatLine(player, season);
                if (line != null) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    private static PlayerStatLine toStatLine(JsonNode player, int season) {
        String position = POSITION_CODE.get(player.path("defaultPositionId").asInt(-1));
        String fullName = player.path("fullName").asText(null);
        JsonNode seasonTotals = seasonTotals(player, season);
        if (position == null || fullName == null || fullName.isBlank() || seasonTotals == null) {
            // A player with no season stat line has nothing to contribute to the join.
            return null;
        }
        boolean goalie = position.equals("G");
        return new PlayerStatLine(
                player.path("id").asLong(),
                fullName,
                position,
                jerseyNumber(player),
                intStat(seasonTotals, GAMES_PLAYED),
                intStat(seasonTotals, HAT_TRICKS),
                goalie ? null : intStat(seasonTotals, SHIFTS),
                goalie ? intStat(seasonTotals, OVERTIME_LOSSES) : null,
                intStat(seasonTotals, goalie ? GOALIE_TIME_ON_ICE : SKATER_TIME_ON_ICE));
    }

    /**
     * ESPN returns many stat entries per player: one per game (statSplitTypeId 5), plus
     * home/away/opponent splits and its own projection (statSourceId 1). The real season
     * total is the actual-source (0), whole-season-split (0) entry.
     */
    private static JsonNode seasonTotals(JsonNode player, int season) {
        for (JsonNode entry : player.path("stats")) {
            if (entry.path("statSourceId").asInt(-1) == 0
                    && entry.path("statSplitTypeId").asInt(-1) == 0
                    && entry.path("scoringPeriodId").asInt(-1) == 0
                    && entry.path("seasonId").asInt(-1) == season) {
                JsonNode stats = entry.path("stats");
                return stats.isObject() ? stats : null;
            }
        }
        return null;
    }

    /** ESPN sends the jersey as a string, and leaves it off for players without one. */
    private static Integer jerseyNumber(JsonNode player) {
        String jersey = player.path("jersey").asText(null);
        if (jersey == null || jersey.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(jersey.trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static Integer intStat(JsonNode stats, int statId) {
        JsonNode value = stats.get(String.valueOf(statId));
        return value == null || !value.isNumber() ? null : (int) Math.round(value.asDouble());
    }
}
