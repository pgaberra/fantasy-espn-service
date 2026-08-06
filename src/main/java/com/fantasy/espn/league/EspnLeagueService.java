package com.fantasy.espn.league;

import com.fantasy.espn.credential.EspnCookies;
import com.fantasy.espn.credential.EspnCredentialService;
import com.fantasy.espn.league.dto.LeagueSettingsResponse;
import com.fantasy.espn.league.dto.LeagueTeam;
import com.fantasy.espn.league.dto.LeagueTeamsResponse;
import com.fantasy.espn.league.dto.RosterSlot;
import com.fantasy.espn.league.dto.StatCategory;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
            Map.entry(34, "GP"));

    private final EspnFantasyClient client;
    private final EspnCredentialService credentialService;

    public EspnLeagueService(EspnFantasyClient client, EspnCredentialService credentialService) {
        this.client = client;
        this.credentialService = credentialService;
    }

    public LeagueSettingsResponse settings(String appUserId, int season, String leagueId) {
        String id = requireNumericLeagueId(leagueId);
        requireValidSeason(season);
        EspnCookies cookies = credentialService.find(appUserId).orElse(null);

        JsonNode root = client.getLeague(season, id, cookies, "mSettings");
        JsonNode settings = root.path("settings");
        if (!settings.isObject()) {
            throw new IllegalStateException("ESPN returned no settings for the league");
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

    public LeagueTeamsResponse teams(String appUserId, int season, String leagueId) {
        String id = requireNumericLeagueId(leagueId);
        requireValidSeason(season);
        EspnCookies cookies = credentialService.find(appUserId).orElse(null);
        String mySwid = cookies == null ? null : normalizeSwid(cookies.swid());

        JsonNode root = client.getLeague(season, id, cookies, "mTeam");
        List<LeagueTeam> teams = new ArrayList<>();
        for (JsonNode team : root.path("teams")) {
            teams.add(new LeagueTeam(teamName(team), isMine(team, mySwid)));
        }
        return new LeagueTeamsResponse(teams);
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
