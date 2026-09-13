package com.fantasy.espn.player;

import com.fantasy.espn.config.EspnProperties;
import com.fantasy.espn.exception.EspnUpstreamException;
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
 * Reads ESPN's player universe: identity, fantasy eligibility and every season stat line the
 * payload carries. Unlike the league endpoints this one is fully public — it needs no league
 * id and no cookies — which is what makes it usable as a source for every user rather than
 * only for the one whose league we are reading.
 *
 * <p>The response is tens of megabytes (every player carries a per-game split for the whole
 * season), so it is parsed as a stream: one player object is materialised at a time and only
 * the season-total entries are kept.
 *
 * <p>Seasons are start years at this boundary and everywhere inside the service. ESPN keys a
 * season by the year it ends in, so a request for 2026 asks ESPN for {@code seasons/2027}.
 */
@Component
public class EspnPlayerClient {

    /** ESPN hockey (fhl) stat ids. Verified against real season lines; ESPN publishes no glossary. */
    private static final int GAMES_PLAYED = 30;
    private static final int GOALS = 13;
    private static final int ASSISTS = 14;
    private static final int PLUS_MINUS = 15;
    private static final int POINTS = 16;
    private static final int PIM = 17;
    private static final int POWER_PLAY_GOALS = 18;
    private static final int SHORTHANDED_GOALS = 20;
    private static final int GAME_WINNING_GOALS = 22;
    private static final int FACEOFFS_WON = 23;
    private static final int FACEOFFS_LOST = 24;
    private static final int SHIFTS = 25;
    private static final int SKATER_TIME_ON_ICE = 26;
    private static final int AVG_TIME_ON_ICE = 27;
    private static final int HAT_TRICKS = 28;
    private static final int SHOTS = 29;
    private static final int HITS = 31;
    private static final int BLOCKED_SHOTS = 32;
    private static final int POWER_PLAY_POINTS = 38;
    private static final int SHORTHANDED_POINTS = 39;

    private static final int GOALIE_GAMES_STARTED = 0;
    private static final int GOALIE_WINS = 1;
    private static final int GOALIE_LOSSES = 2;
    private static final int GOALIE_SHOTS_AGAINST = 3;
    private static final int GOALIE_GOALS_AGAINST = 4;
    private static final int GOALIE_SAVES = 6;
    private static final int GOALIE_SHUTOUTS = 7;
    private static final int GOALIE_TIME_ON_ICE = 8;
    private static final int GOALIE_OVERTIME_LOSSES = 9;
    private static final int GOALIE_GOALS_AGAINST_AVG = 10;
    private static final int GOALIE_SAVE_PCTG = 11;

    /** ESPN's defaultPositionId — the player's primary position. */
    private static final Map<Integer, String> DEFAULT_POSITION_CODE =
            Map.of(1, "C", 2, "LW", 3, "RW", 4, "D", 5, "G");

    /**
     * ESPN's lineup-slot ids, which number the positions differently from defaultPositionId.
     * Only the real positions are mapped: 3 (F), 6 (Util), 7 (BN) and 8 (IR) are roster slots
     * rather than eligibility, and Yahoo never reported them either.
     */
    private static final Map<Integer, String> LINEUP_SLOT_CODE =
            Map.of(0, "C", 1, "LW", 2, "RW", 4, "D", 5, "G");

    /** Emitted in this order, so a player's positions read the same way every sync. */
    private static final List<String> POSITION_ORDER = List.of("C", "LW", "RW", "D", "G");

    /**
     * ESPN's proTeamId. Its abbreviations already match the ones the app shows (LA, NJ, SJ and
     * TB rather than the NHL's LAK, NJD, SJS, TBL), so they are passed through as they are.
     * Id 0 is ESPN's free-agent bucket and has no team.
     */
    private static final Map<Integer, String> TEAM_ABBREV = Map.ofEntries(
            Map.entry(1, "BOS"), Map.entry(2, "BUF"), Map.entry(3, "CGY"), Map.entry(4, "CHI"),
            Map.entry(5, "DET"), Map.entry(6, "EDM"), Map.entry(7, "CAR"), Map.entry(8, "LA"),
            Map.entry(9, "DAL"), Map.entry(10, "MTL"), Map.entry(11, "NJ"), Map.entry(12, "NYI"),
            Map.entry(13, "NYR"), Map.entry(14, "OTT"), Map.entry(15, "PHI"), Map.entry(16, "PIT"),
            Map.entry(17, "COL"), Map.entry(18, "SJ"), Map.entry(19, "STL"), Map.entry(20, "TB"),
            Map.entry(21, "TOR"), Map.entry(22, "VAN"), Map.entry(23, "WSH"), Map.entry(25, "ANA"),
            Map.entry(26, "FLA"), Map.entry(27, "NSH"), Map.entry(28, "WPG"), Map.entry(29, "CBJ"),
            Map.entry(30, "MIN"), Map.entry(37, "VGK"), Map.entry(124292, "SEA"),
            Map.entry(129764, "UTA"));

    /**
     * ESPN's image CDN, asked for the size the player table draws. The URL is built from the
     * player's id and is therefore always producible — but ESPN has no picture for roughly one
     * player in seven and answers those with a 404, so {@link EspnHeadshotVerifier} drops the
     * ones that do not resolve before the pool is stored.
     */
    private static final String HEADSHOT_URL =
            "https://a.espncdn.com/combiner/i?img=/i/headshots/nhl/players/full/%d.png&w=64&h=64";

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

    /**
     * Every player ESPN lists for the given season, which is the pool that season is played
     * with. Each player carries the season totals the payload holds — that season and the one
     * before it.
     *
     * @param season the start year: 2026 is the 2026-27 season
     */
    public List<FetchedPlayer> fetchPlayers(int season) {
        int espnSeason = season + 1;
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/apis/v3/games/{gameKey}/seasons/{season}/players")
                            .queryParam("scoringPeriodId", 0)
                            .queryParam("view", "kona_player_info")
                            .build(gameKey, espnSeason))
                    .header(PLAYER_FILTER_HEADER, ALL_PLAYERS_FILTER)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status < 200 || status >= 300) {
                            throw new EspnUpstreamException(
                                    "ESPN player call failed (HTTP " + status + ")");
                        }
                        return parse(response.getBody());
                    });
        } catch (RestClientException e) {
            throw new EspnUpstreamException("ESPN player call failed: " + e.getMessage(), e);
        }
    }

    private List<FetchedPlayer> parse(InputStream body) throws IOException {
        List<FetchedPlayer> players = new ArrayList<>();
        try (JsonParser parser = objectMapper.getFactory().createParser(body)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new EspnUpstreamException("ESPN player response was not a JSON array");
            }
            while (parser.nextToken() == JsonToken.START_OBJECT) {
                JsonNode node = parser.readValueAsTree();
                FetchedPlayer player = toPlayer(node);
                if (player != null) {
                    players.add(player);
                }
            }
        }
        return players;
    }

    private static FetchedPlayer toPlayer(JsonNode node) {
        String position = DEFAULT_POSITION_CODE.get(node.path("defaultPositionId").asInt(-1));
        String fullName = text(node, "fullName");
        if (position == null || fullName == null) {
            // Without a position or a name there is nothing to put in the pool.
            return null;
        }
        boolean goalie = position.equals("G");
        long id = node.path("id").asLong();
        List<FetchedSkaterSeason> skaterSeasons = new ArrayList<>();
        List<FetchedGoalieSeason> goalieSeasons = new ArrayList<>();
        for (JsonNode totals : seasonTotals(node)) {
            int season = totals.path("seasonId").asInt() - 1;
            JsonNode stats = totals.path("stats");
            if (goalie) {
                goalieSeasons.add(toGoalieSeason(season, stats));
            } else {
                skaterSeasons.add(toSkaterSeason(season, stats));
            }
        }
        return new FetchedPlayer(
                id,
                firstName(node, fullName),
                lastName(node, fullName),
                fullName,
                position,
                eligiblePositions(node, position),
                jerseyNumber(node),
                TEAM_ABBREV.get(node.path("proTeamId").asInt(-1)),
                HEADSHOT_URL.formatted(id),
                node.path("active").asBoolean(false),
                goalie,
                List.copyOf(skaterSeasons),
                List.copyOf(goalieSeasons));
    }

    /**
     * ESPN returns many stat entries per player: one per game (statSplitTypeId 5), plus
     * home/away/opponent splits and its own projection (statSourceId 1). The real season totals
     * are the actual-source (0), whole-season-split (0) entries — one per season the payload
     * reaches back to. An entry with no stats at all says nothing and is left out.
     */
    private static List<JsonNode> seasonTotals(JsonNode player) {
        List<JsonNode> totals = new ArrayList<>();
        for (JsonNode entry : player.path("stats")) {
            JsonNode stats = entry.path("stats");
            if (entry.path("statSourceId").asInt(-1) == 0
                    && entry.path("statSplitTypeId").asInt(-1) == 0
                    && entry.path("scoringPeriodId").asInt(-1) == 0
                    && stats.isObject()
                    && !stats.isEmpty()) {
                totals.add(entry);
            }
        }
        return totals;
    }

    private static FetchedSkaterSeason toSkaterSeason(int season, JsonNode stats) {
        Integer goals = intStat(stats, GOALS);
        Integer shots = intStat(stats, SHOTS);
        Integer faceoffsWon = intStat(stats, FACEOFFS_WON);
        Integer faceoffsLost = intStat(stats, FACEOFFS_LOST);
        return new FetchedSkaterSeason(
                season,
                intStat(stats, GAMES_PLAYED),
                goals,
                intStat(stats, ASSISTS),
                intStat(stats, POINTS),
                intStat(stats, PLUS_MINUS),
                intStat(stats, PIM),
                intStat(stats, POWER_PLAY_GOALS),
                intStat(stats, POWER_PLAY_POINTS),
                intStat(stats, SHORTHANDED_GOALS),
                intStat(stats, SHORTHANDED_POINTS),
                intStat(stats, GAME_WINNING_GOALS),
                shots,
                ratio(goals, shots),
                minutesSeconds(doubleStat(stats, AVG_TIME_ON_ICE)),
                faceoffPct(faceoffsWon, faceoffsLost),
                intStat(stats, HITS),
                intStat(stats, BLOCKED_SHOTS),
                faceoffsWon,
                faceoffsLost,
                intStat(stats, HAT_TRICKS),
                intStat(stats, SHIFTS),
                intStat(stats, SKATER_TIME_ON_ICE));
    }

    private static FetchedGoalieSeason toGoalieSeason(int season, JsonNode stats) {
        return new FetchedGoalieSeason(
                season,
                intStat(stats, GAMES_PLAYED),
                intStat(stats, GOALIE_GAMES_STARTED),
                intStat(stats, GOALIE_WINS),
                intStat(stats, GOALIE_LOSSES),
                intStat(stats, GOALIE_SHUTOUTS),
                intStat(stats, GOALIE_SHOTS_AGAINST),
                intStat(stats, GOALIE_SAVES),
                intStat(stats, GOALIE_GOALS_AGAINST),
                doubleStat(stats, GOALIE_GOALS_AGAINST_AVG),
                doubleStat(stats, GOALIE_SAVE_PCTG),
                intStat(stats, HAT_TRICKS),
                intStat(stats, GOALIE_OVERTIME_LOSSES),
                intStat(stats, GOALIE_TIME_ON_ICE));
    }

    /**
     * ESPN's eligibleSlots, in a fixed order and without the roster-only slots. A player whose
     * slots carry no real position keeps their primary one, so the list is never empty.
     */
    private static List<String> eligiblePositions(JsonNode player, String position) {
        List<String> positions = new ArrayList<>();
        for (JsonNode slot : player.path("eligibleSlots")) {
            String mapped = LINEUP_SLOT_CODE.get(slot.asInt(-1));
            if (mapped != null && !positions.contains(mapped)) {
                positions.add(mapped);
            }
        }
        if (positions.isEmpty()) {
            return List.of(position);
        }
        return POSITION_ORDER.stream().filter(positions::contains).toList();
    }

    /** ESPN sends the jersey as a string, and leaves it off for players without one. */
    private static Integer jerseyNumber(JsonNode player) {
        String jersey = text(player, "jersey");
        if (jersey == null) {
            return null;
        }
        try {
            return Integer.valueOf(jersey.trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static String firstName(JsonNode player, String fullName) {
        String firstName = text(player, "firstName");
        if (firstName != null) {
            return firstName;
        }
        int split = fullName.lastIndexOf(' ');
        return split > 0 ? fullName.substring(0, split) : fullName;
    }

    private static String lastName(JsonNode player, String fullName) {
        String lastName = text(player, "lastName");
        if (lastName != null) {
            return lastName;
        }
        int split = fullName.lastIndexOf(' ');
        return split > 0 ? fullName.substring(split + 1) : fullName;
    }

    /** Yahoo reported a shooting percentage; ESPN does not, so it comes from the two counts. */
    private static Double ratio(Integer part, Integer whole) {
        if (part == null || whole == null || whole == 0) {
            return null;
        }
        return (double) part / whole;
    }

    private static Double faceoffPct(Integer won, Integer lost) {
        if (won == null && lost == null) {
            return null;
        }
        int w = won == null ? 0 : won;
        int total = w + (lost == null ? 0 : lost);
        return total == 0 ? null : (double) w / total;
    }

    /** ESPN reports average time on ice in seconds; the read model carries Yahoo's "MM:SS". */
    private static String minutesSeconds(Double seconds) {
        if (seconds == null || seconds <= 0) {
            return null;
        }
        int total = (int) Math.round(seconds);
        return "%d:%02d".formatted(total / 60, total % 60);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            return null;
        }
        return value.asText();
    }

    private static Integer intStat(JsonNode stats, int statId) {
        JsonNode value = stats.get(String.valueOf(statId));
        return value == null || !value.isNumber() ? null : (int) Math.round(value.asDouble());
    }

    private static Double doubleStat(JsonNode stats, int statId) {
        JsonNode value = stats.get(String.valueOf(statId));
        return value == null || !value.isNumber() ? null : value.asDouble();
    }
}
