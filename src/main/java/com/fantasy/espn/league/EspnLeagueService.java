package com.fantasy.espn.league;

import com.fantasy.espn.config.EspnProperties;
import com.fantasy.espn.credential.EspnCookies;
import com.fantasy.espn.credential.EspnCredentialService;
import com.fantasy.espn.exception.EspnLeagueNotFoundException;
import com.fantasy.espn.exception.EspnUpstreamException;
import com.fantasy.espn.league.dto.AvailablePlayer;
import com.fantasy.espn.league.dto.DraftStatus;
import com.fantasy.espn.league.dto.LeagueDraftPick;
import com.fantasy.espn.league.dto.LeagueDraftResponse;
import com.fantasy.espn.league.dto.LeagueDraftTeam;
import com.fantasy.espn.player.EspnPlayerFields;
import com.fantasy.espn.league.dto.EspnAvailability;
import com.fantasy.espn.league.dto.LeagueSettingsResponse;
import com.fantasy.espn.league.dto.LeagueTeam;
import com.fantasy.espn.league.dto.LeagueTeamsResponse;
import com.fantasy.espn.league.dto.RosterSlot;
import com.fantasy.espn.league.dto.StatCategory;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
            String appUserId, Integer season, String leagueId, int limit) {
        String id = requireNumericLeagueId(leagueId);
        EspnCookies cookies = credentialService.find(appUserId).orElse(null);

        JsonNode root = fetchAvailable(season, id, cookies, limit);
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

    private JsonNode fetchAvailable(Integer requestedSeason, String id, EspnCookies cookies, int limit) {
        if (requestedSeason != null) {
            requireValidSeason(requestedSeason);
            return client.getAvailablePlayers(requestedSeason, id, cookies, limit);
        }
        try {
            return client.getAvailablePlayers(configuredSeason, id, cookies, limit);
        } catch (EspnLeagueNotFoundException notFoundForCurrentSeason) {
            return client.getAvailablePlayers(configuredSeason - 1, id, cookies, limit);
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

    public LeagueTeamsResponse teams(String appUserId, Integer season, String leagueId) {
        String id = requireNumericLeagueId(leagueId);
        EspnCookies cookies = credentialService.find(appUserId).orElse(null);
        String mySwid = cookies == null ? null : normalizeSwid(cookies.swid());

        JsonNode root = fetchLeague(season, id, cookies, "mTeam", "mSettings");
        JsonNode pickOrder = root.path("settings").path("draftSettings").path("pickOrder");
        List<LeagueTeam> teams = new ArrayList<>();
        Integer draftPosition = null;
        int seat = 0;
        for (JsonNode team : inDraftOrder(root.path("teams"), pickOrder)) {
            seat++;
            boolean mine = isMine(team, mySwid);
            if (mine && draftPosition == null && namesTeam(pickOrder, team)) {
                draftPosition = seat;
            }
            teams.add(new LeagueTeam(teamName(team), mine));
        }
        return new LeagueTeamsResponse(teams, draftPosition);
    }

    /**
     * The league's draft for the configured season: its status, its teams in draft order and the
     * picks made so far.
     *
     * <p>Unlike the settings read this never falls back to the season before. It is polled while
     * a draft room follows the draft, and last season's finished draft would read as this one
     * having ended.
     */
    public LeagueDraftResponse draft(String appUserId, String leagueId) {
        String id = requireNumericLeagueId(leagueId);
        EspnCookies cookies = credentialService.find(appUserId).orElse(null);
        String mySwid = cookies == null ? null : normalizeSwid(cookies.swid());

        JsonNode root = client.getLeague(configuredSeason, id, cookies, "mDraftDetail", "mTeam", "mSettings");
        JsonNode draftSettings = root.path("settings").path("draftSettings");
        JsonNode detail = root.path("draftDetail");
        int teamCount = root.path("teams").size();
        List<LeagueDraftPick> picks = parseDraftPicks(detail.path("picks"), teamCount);

        List<Integer> order = draftOrder(draftSettings.path("pickOrder"), picks, root.path("teams"));
        List<LeagueDraftTeam> teams = new ArrayList<>();
        for (JsonNode team : inDraftOrder(root.path("teams"), order)) {
            teams.add(new LeagueDraftTeam(team.path("id").asInt(), teamName(team), isMine(team, mySwid)));
        }
        return new LeagueDraftResponse(
                id,
                configuredSeason,
                draftStatus(detail),
                "AUCTION".equals(text(draftSettings, "type")),
                teams,
                !order.isEmpty(),
                picks);
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

    /**
     * The first-round order as team ids, but only when it places every team exactly once: from the
     * pick order ESPN settles before the draft, or failing that from the first round's own picks
     * once they have been made. A partial order is no order, and an empty list says so.
     */
    private static List<Integer> draftOrder(JsonNode pickOrder, List<LeagueDraftPick> picks, JsonNode teams) {
        Set<Integer> teamIds = new HashSet<>();
        for (JsonNode team : teams) {
            teamIds.add(team.path("id").asInt(Integer.MIN_VALUE));
        }
        List<Integer> fromSettings = new ArrayList<>();
        for (JsonNode teamId : pickOrder) {
            fromSettings.add(teamId.asInt(Integer.MIN_VALUE));
        }
        if (placesEveryTeam(fromSettings, teamIds)) {
            return fromSettings;
        }
        List<Integer> fromFirstRound = picks.stream()
                .filter(pick -> pick.round() == 1)
                .map(LeagueDraftPick::teamId)
                .toList();
        return placesEveryTeam(fromFirstRound, teamIds) ? fromFirstRound : List.of();
    }

    private static boolean placesEveryTeam(List<Integer> order, Set<Integer> teamIds) {
        return !teamIds.isEmpty() && order.size() == teamIds.size() && new HashSet<>(order).equals(teamIds);
    }

    /**
     * The picks made, by overall number. ESPN numbers each pick overall and within its round;
     * the overall number is derived from the round's where it is missing. An entry with no player
     * is not a pick yet.
     */
    private static List<LeagueDraftPick> parseDraftPicks(JsonNode entries, int teamCount) {
        List<LeagueDraftPick> picks = new ArrayList<>();
        for (JsonNode entry : entries) {
            long playerId = entry.path("playerId").asLong(-1);
            int teamId = entry.path("teamId").asInt(Integer.MIN_VALUE);
            int round = entry.path("roundId").asInt(0);
            int overall = entry.path("overallPickNumber").asInt(0);
            int roundPick = entry.path("roundPickNumber").asInt(0);
            if (overall <= 0 && round > 0 && roundPick > 0 && teamCount > 0) {
                overall = (round - 1) * teamCount + roundPick;
            }
            if (playerId <= 0 || teamId == Integer.MIN_VALUE || overall <= 0) {
                continue;
            }
            picks.add(new LeagueDraftPick(overall, round, teamId, playerId, entry.path("keeper").asBoolean(false)));
        }
        picks.sort(Comparator.comparingInt(LeagueDraftPick::pick));
        return picks;
    }

    /**
     * Whether ESPN's pick order names this team. Only then is its place in the returned list its
     * real seat; a team the order does not name sits in ESPN's arbitrary team order, where a seat
     * read off the list would be a guess presented as a fact.
     */
    private static boolean namesTeam(JsonNode pickOrder, JsonNode team) {
        if (!pickOrder.isArray()) {
            return false;
        }
        int teamId = team.path("id").asInt(Integer.MIN_VALUE);
        if (teamId == Integer.MIN_VALUE) {
            return false;
        }
        for (JsonNode pick : pickOrder) {
            if (pick.asInt(Integer.MIN_VALUE) == teamId) {
                return true;
            }
        }
        return false;
    }

    /**
     * The teams in the league's draft order ({@code draftSettings.pickOrder}, team ids in the order
     * they pick in the first round), which a draft setup needs; ESPN lists {@code teams} by id. Any
     * team the order does not name follows in ESPN's order, so a league without one is unchanged.
     */
    private static List<JsonNode> inDraftOrder(JsonNode teams, JsonNode pickOrder) {
        List<Integer> order = new ArrayList<>();
        for (JsonNode teamId : pickOrder) {
            order.add(teamId.asInt(Integer.MIN_VALUE));
        }
        return inDraftOrder(teams, order);
    }

    private static List<JsonNode> inDraftOrder(JsonNode teams, List<Integer> order) {
        Map<Integer, JsonNode> remaining = new LinkedHashMap<>();
        for (JsonNode team : teams) {
            remaining.put(team.path("id").asInt(Integer.MIN_VALUE), team);
        }
        List<JsonNode> ordered = new ArrayList<>();
        for (int teamId : order) {
            JsonNode team = remaining.remove(teamId);
            if (team != null) {
                ordered.add(team);
            }
        }
        ordered.addAll(remaining.values());
        return ordered;
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
