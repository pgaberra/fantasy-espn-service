package com.fantasy.espn.player.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One ESPN player's season stat line, limited to the stats Yahoo does not report. Values are
 * null when ESPN reports nothing for that stat (e.g. shifts for a goalie).
 */
public record PlayerStatLine(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "ESPN's player id")
        long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String fullName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "C, LW, RW, D or G")
        String position,
        @Schema(description = "Jersey number, which is what separates two players who share a name")
        Integer sweaterNumber,
        Integer gamesPlayed,
        Integer hatTricks,
        Integer shifts,
        @Schema(description = "Goalie overtime losses")
        Integer overtimeLosses,
        @Schema(description = "Total time on ice for the season, in seconds")
        Integer timeOnIce) {
}
