package com.fantasy.espn.player.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A skater from the cached read model: identity, ESPN eligible positions and one season's stat
 * line. Bio fields are required; stat fields are nullable (no recorded stats that season).
 */
public record SkaterResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "ESPN's player id")
        long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String firstName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String lastName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String position,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "ESPN fantasy eligible positions (e.g. [\"C\",\"LW\"])")
        List<String> eligiblePositions,
        Integer sweaterNumber,
        String teamAbbrev,
        @Schema(description = "ESPN image CDN URL for the player's headshot, already sized for "
                + "the player table")
        String headshot,
        Integer gamesPlayed,
        Integer goals,
        Integer assists,
        Integer points,
        Integer plusMinus,
        Integer pim,
        Integer powerPlayGoals,
        Integer powerPlayPoints,
        Integer shorthandedGoals,
        Integer shorthandedPoints,
        Integer gameWinningGoals,
        Integer shots,
        Double shootingPctg,
        String avgToi,
        Double faceoffWinningPctg,
        Integer hits,
        Integer blockedShots,
        Integer totalFaceoffWins,
        Integer totalFaceoffLosses,
        @Schema(description = "Stats Yahoo does not report at all")
        Integer hatTricks,
        Integer shifts,
        @Schema(description = "Total time on ice for the season, in seconds")
        Integer timeOnIce
) {
}
