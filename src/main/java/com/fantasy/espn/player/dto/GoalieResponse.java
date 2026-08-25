package com.fantasy.espn.player.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A goalie from the cached read model: identity, ESPN eligible positions and one season's stat
 * line. Bio fields are required; stat fields are nullable (no recorded stats that season).
 */
public record GoalieResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "ESPN's player id")
        long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String firstName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String lastName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String position,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "ESPN fantasy eligible positions (e.g. [\"G\"])")
        List<String> eligiblePositions,
        Integer sweaterNumber,
        String teamAbbrev,
        @Schema(description = "ESPN image CDN URL for the player's headshot, already sized for "
                + "the player table")
        String headshot,
        Integer gamesPlayed,
        Integer gamesStarted,
        Integer wins,
        Integer losses,
        Integer shutouts,
        Integer shotsAgainst,
        Integer saves,
        Integer goalsAgainst,
        Double goalsAgainstAvg,
        Double savePctg,
        @Schema(description = "Stats Yahoo does not report at all")
        Integer overtimeLosses,
        @Schema(description = "Total time on ice for the season, in seconds")
        Integer timeOnIce
) {
}
