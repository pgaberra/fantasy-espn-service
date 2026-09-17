package com.fantasy.espn.player;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * How ESPN numbers positions and clubs, and the reading of a player node that follows from it.
 *
 * <p>Shared because two places read the same player shape from two different ESPN documents: the
 * nightly pool sync ({@link EspnPlayerClient}) and a league's available players. A second copy of
 * these maps is a position that goes wrong in one place only.
 */
public final class EspnPlayerFields {

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

    private EspnPlayerFields() {
    }

    /** The player's primary position, or null for a node ESPN gives no position. */
    public static String position(JsonNode player) {
        return DEFAULT_POSITION_CODE.get(player.path("defaultPositionId").asInt(-1));
    }

    /** The club ESPN has him on, or null for one it lists as a free agent (proTeamId 0). */
    public static String teamAbbrev(JsonNode player) {
        return TEAM_ABBREV.get(player.path("proTeamId").asInt(-1));
    }

    /**
     * ESPN's eligibleSlots, in a fixed order and without the roster-only slots. A player whose
     * slots carry no real position keeps their primary one, so the list is never empty.
     */
    public static List<String> eligiblePositions(JsonNode player, String position) {
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
    public static Integer jerseyNumber(JsonNode player) {
        JsonNode jersey = player.path("jersey");
        if (!jersey.isTextual() || jersey.asText().isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(jersey.asText().trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
