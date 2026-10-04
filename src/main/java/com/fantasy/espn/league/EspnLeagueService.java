package com.fantasy.espn.league;

import com.fantasy.espn.config.EspnProperties;
import com.fantasy.espn.credential.EspnCookies;
import com.fantasy.espn.credential.EspnCredentialService;
import com.fantasy.espn.exception.EspnLeagueNotFoundException;
import com.fantasy.espn.exception.EspnUpstreamException;
import com.fantasy.espn.league.dto.AvailablePlayer;
import com.fantasy.espn.league.dto.DraftStatus;
import com.fantasy.espn.league.dto.LeagueRosterPlayer;
import com.fantasy.espn.league.dto.LeagueRosterTeam;
import com.fantasy.espn.league.dto.LeagueRostersResponse;
import com.fantasy.espn.player.EspnPlayerFields;
import com.fantasy.espn.league.dto.EspnAvailability;
import com.fantasy.espn.league.dto.LeagueSettingsResponse;
import com.fantasy.espn.league.dto.RosterSlot;
import com.fantasy.espn.league.dto.StatCategory;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads a user's ESPN fantasy hockey league and maps ESPN's JSON into clean DTOs. Uses the
 * user's stored cookies when present (private leagues); public leagues are read without them.
 */
@Service
public class EspnLeagueService {

    // ESPN hockey (fhl) lineup-slot ids -> position codes.
    private static final Map<String, String> SLOT_CODE = Map.ofEntries(
            Map.entry("0", "C"),
            Map.entry("1", "LW"),
            Map.entry("2", "RW"),
            Map.entry("3", "F"),
            Map.entry("4", "D"),
            Map.entry("5", "G"),
            Map.entry("6", "Util"),
            Map.entry("7", "BN"),
            Map.entry("8", "IR"));

    // ESPN hockey (fhl) stat ids -> human abbreviations. The BFF maps ids to projection stats;
    // this label is only for readable "unsupported stat" reporting.
    private static final Map<Integer, String> STAT_ABBREV = Map.ofEntries(
            Map.entry(0, "GS"),
            Map.entry(1, "W"),
            Map.entry(2, "L"),
            Map.entry(3, "SA"),
            Map.entry(4, "GA"),
            Map.entry(6, "SV"),
            Map.entry(7, "SO"),
            Map.entry(10, "GAA"),
            Map.entry(11, "SV%"),
            Map.entry(13, "G"),
            Map.entry(14, "A"),
            Map.entry(15, "+/-"),
            Map.entry(17, "PIM"),
            Map.entry(18, "PPG"),
            Map.entry(19, "PPA"),
            Map.entry(20, "SHG"),
            Map.entry(21, "SHA"),
            Map.entry(22, "GWG"),
            Map.entry(23, "FOW"),
            Map.entry(24, "FOL"),
            Map.entry(27, "ATOI"),
            Map.entry(29, "SOG"),
            Map.entry(31, "HIT"),
            Map.entry(32, "BLK"),
            Map.entry(34, "GP"),
            // ESPN scores a few stats the projection domain has no equivalent for. They are
            // named here so they read as "HAT"/"OTL" rather than "STAT_28" when the BFF reports
            // them as unsupported.
            Map.entry(9, "OTL"),
            Map.entry(28, "HAT"),
            Map.entry(33, "DEF"),
            Map.entry(35, "STPG"),
            Map.entry(36, "STPA"),
            Map.entry(37, "STP"),
            Map.entry(38, "PPP"),
            Map.entry(39, "SHP"));

    private final EspnFantasyClient client;
    private final EspnCredentialService credentialService;
    private final int configuredSeason;

    public EspnLeagueService(EspnFantasyClient client, EspnCredentialService credentialService,
                             EspnProperties props) {
        this.client = client;
        this.credentialService = credentialService;
        this.configuredSeason = props.season();
    }

    /**
     * Reads a league for the configured season, falling back to the season before it when ESPN
     * has no league there — a user who hasn't renewed for the coming season still syncs the
     * settings they play by. An explicit season overrides both.
     */
    private JsonNode fetchLeague(Integer requestedSeason, String id, EspnCookies cookies, String... views) {
        if (requestedSeason != null) {
            requireValidSeason(requestedSeason);
            return client.getLeague(requestedSeason, id, cookies, views);
        }
        try {
            return client.getLeague(configuredSeason, id, cookies, views);
        } catch (EspnLeagueNotFoundException notFoundForCurrentSeason) {
            return client.getLeague(configuredSeason - 1, id, cookies, views);
        }
    }

    public LeagueSettingsResponse settings(String appUserId, Integer season, String leagueId) {
        String id = requireNumericLeagueId(leagueId);
        EspnCookies cookies = credentialService.find(appUserId).orElse(null);

        JsonNode root = fetchLeague(season, id, cookies, "mSettings");
        JsonNode settings = root.path("settings");
        if (!settings.isObject()) {
            throw new EspnUpstreamException("ESPN returned no settings for the league");
        }
        JsonNode scoring = settings.path("scoringSettings");
        return new LeagueSettingsResponse(
                id,
                text(settings, "name"),
                text(scoring, "scoringType"),
                intOrNull(settings.path("size")),
                parseStatCategories(scoring.path("scoringItems")),
                parseRosterSlots(settings.path("rosterSettings").path("lineupSlotCounts")));
    }

    /**
     * The players the league has available, capped at {@code limit}.
     *
     * <p>ESPN answers this only for the league-scoped player document, so it needs the user's
     * cookies like any private read: without them ESPN refuses and the caller gets the same 400 a
     * private league gives. The ids are ESPN's own, as everywhere else here.
     */
    public List<AvailablePlayer> freeAgents(
            String appUserId, Integer season, String leagueId, String position, int limit) {
        String id = requireNumericLeagueId(leagueId);
        EspnCookies cookies = credentialService.find(appUserId).orElse(null);

        JsonNode root = fetchAvailable(season, id, cookies, position, limit);
        List<AvailablePlayer> available = new ArrayList<>();
        // ESPN has served this document both as a bare array and wrapped in `players`.
        JsonNode entries = root.isArray() ? root : root.path("players");
        for (JsonNode entry : entries) {
            AvailablePlayer player = toAvailablePlayer(entry);
            if (player != null && available.size() < limit) {
                available.add(player);
            }
        }
        return List.copyOf(available);
    }

    private JsonNode fetchAvailable(
            Integer requestedSeason, String id, EspnCookies cookies, String position, int limit) {
        if (requestedSeason != null) {
            requireValidSeason(requestedSeason);
            return client.getAvailablePlayers(requestedSeason, id, cookies, position, limit);
        }
        try {
            return client.getAvailablePlayers(configuredSeason, id, cookies, position, limit);
        } catch (EspnLeagueNotFoundException notFoundForCurrentSeason) {
            return client.getAvailablePlayers(configuredSeason - 1, id, cookies, position, limit);
        }
    }

    /**
     * One entry of the league's player document. The status lives on the pool entry and the
     * identity on the player inside it, so both halves are read.
     */
    private static AvailablePlayer toAvailablePlayer(JsonNode entry) {
        JsonNode player = entry.path("player").isObject() ? entry.path("player") : entry;
        String position = EspnPlayerFields.position(player);
        String fullName = text(player, "fullName");
        if (position == null || fullName == null) {
            return null;
        }
        String status = text(entry, "status");
        return new AvailablePlayer(
                player.path("id").asLong(),
                fullName,
                EspnPlayerFields.teamAbbrev(player),
                position,
                EspnPlayerFields.jerseyNumber(player),
                position.equals("G"),
                EspnPlayerFields.eligiblePositions(player, position),
                EspnAvailability.of(status));
    }

    /**
     * The league's teams as they stand today, each with the players on its roster, for the
     * configured season.
     *
     * <p>Unlike the settings read this never falls back to the season before: a league ranked on
     * last season's rosters would read as this one's, and one that has not drafted yet is an answer
     * of its own (empty rosters) rather than a reason to look elsewhere.
     */
    public LeagueRostersResponse rosters(String appUserId, String leagueId) {
        String id = requireNumericLeagueId(leagueId);
        EspnCookies cookies = credentialService.find(appUserId).orElse(null);
        String mySwid = cookies == null ? null : normalizeSwid(cookies.swid());

        JsonNode root = client.getLeague(
                configuredSeason, id, cookies, "mRoster", "mTeam", "mSettings", "mDraftDetail");
        List<LeagueRosterTeam> teams = new ArrayList<>();
        for (JsonNode team : root.path("teams")) {
            List<LeagueRosterPlayer> players = rosteredPlayers(team);
            teams.add(new LeagueRosterTeam(
                    team.path("id").asInt(), teamName(team), isMine(team, mySwid),
                    players.stream().map(LeagueRosterPlayer::espnId).toList(), players));
        }
        return new LeagueRostersResponse(
                id,
                configuredSeason,
                text(root.path("settings"), "name"),
                draftStatus(root.path("draftDetail")),
                teams);
    }

    /** The players on a team's roster, once each; an entry with no player is an empty slot. */
    private static List<LeagueRosterPlayer> rosteredPlayers(JsonNode team) {
        Set<Long> seen = new LinkedHashSet<>();
        List<LeagueRosterPlayer> players = new ArrayList<>();
        for (JsonNode entry : team.path("roster").path("entries")) {
            long playerId = entry.path("playerId").asLong(-1);
            if (playerId > 0 && seen.add(playerId)) {
                players.add(rosterPlayer(playerId, entry));
            }
        }
        return List.copyOf(players);
    }

    /**
     * A roster entry: the slot is on the entry, the identity on the pool entry's player. A healthy
     * player's ACTIVE (or NORMAL) status is reported as no status at all.
     */
    private static LeagueRosterPlayer rosterPlayer(long playerId, JsonNode entry) {
        JsonNode player = entry.path("playerPoolEntry").path("player");
        String position = EspnPlayerFields.position(player);
        String injury = text(player, "injuryStatus");
        if (injury == null) {
            injury = text(entry, "injuryStatus");
        }
        if (injury != null && (injury.isBlank() || injury.equals("ACTIVE") || injury.equals("NORMAL"))) {
            injury = null;
        }
        return new LeagueRosterPlayer(
                playerId,
                text(player, "fullName"),
                EspnPlayerFields.teamAbbrev(player),
                EspnPlayerFields.jerseyNumber(player),
                "G".equals(position),
                position == null ? List.of() : EspnPlayerFields.eligiblePositions(player, position),
                entry.has("lineupSlotId") ? SLOT_CODE.get(entry.path("lineupSlotId").asText()) : null,
                injury);
    }

    static DraftStatus draftStatus(JsonNode detail) {
        if (!detail.isObject()) {
            return DraftStatus.UNKNOWN;
        }
        if (detail.path("drafted").asBoolean(false)) {
            return DraftStatus.FINISHED;
        }
        return detail.path("inProgress").asBoolean(false) ? DraftStatus.IN_PROGRESS : DraftStatus.PRE_DRAFT;
    }

    private static List<StatCategory> parseStatCategories(JsonNode scoringItems) {
        List<StatCategory> categories = new ArrayList<>();
        if (!scoringItems.isArray()) {
            return categories;
        }
        for (JsonNode item : scoringItems) {
            int statId = item.path("statId").asInt(-1);
            if (statId < 0) {
                continue;
            }
            categories.add(new StatCategory(
                    statId,
                    STAT_ABBREV.getOrDefault(statId, "STAT_" + statId),
                    doubleOrNull(item.path("points"))));
        }
        return categories;
    }

    private static List<RosterSlot> parseRosterSlots(JsonNode lineupSlotCounts) {
        List<RosterSlot> slots = new ArrayList<>();
        if (!lineupSlotCounts.isObject()) {
            return slots;
        }
        lineupSlotCounts.fields().forEachRemaining(entry -> {
            int count = entry.getValue().asInt(0);
            if (count > 0) {
                slots.add(new RosterSlot(SLOT_CODE.getOrDefault(entry.getKey(), "SLOT_" + entry.getKey()), count));
            }
        });
        return slots;
    }

    private static String teamName(JsonNode team) {
        String name = text(team, "name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        String location = orEmpty(text(team, "location"));
        String nickname = orEmpty(text(team, "nickname"));
        String combined = (location + " " + nickname).trim();
        return combined.isBlank() ? "Team " + team.path("id").asText("") : combined;
    }

    private static boolean isMine(JsonNode team, String mySwid) {
        if (mySwid == null) {
            return false;
        }
        for (JsonNode owner : team.path("owners")) {
            if (owner.isTextual() && normalizeSwid(owner.asText()).equals(mySwid)) {
                return true;
            }
        }
        JsonNode primaryOwner = team.path("primaryOwner");
        return primaryOwner.isTextual() && normalizeSwid(primaryOwner.asText()).equals(mySwid);
    }

    private static String normalizeSwid(String swid) {
        return swid.replace("{", "").replace("}", "").trim().toUpperCase(Locale.ROOT);
    }

    private static String requireNumericLeagueId(String leagueId) {
        if (leagueId == null || !leagueId.matches("\\d{1,20}")) {
            throw new IllegalArgumentException("leagueId must be a numeric ESPN league id");
        }
        return leagueId;
    }

    private static void requireValidSeason(int season) {
        if (season < 2000 || season > 2100) {
            throw new IllegalArgumentException("season must be a valid year");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return (value.isMissingNode() || value.isNull()) ? null : value.asText();
    }

    private static Integer intOrNull(JsonNode value) {
        return value.isNumber() ? value.asInt() : null;
    }

    private static Double doubleOrNull(JsonNode value) {
        return value.isNumber() ? value.asDouble() : null;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
