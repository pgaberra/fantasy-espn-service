package com.fantasy.espn.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** A team in a league and the players it holds today. */
public record LeagueRosterTeam(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "ESPN's id for the team, unique within the league.", example = "3")
        int teamId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "True for the authenticated user's own team (matched via their SWID; "
                        + "always false for a league read without cookies).") boolean mine,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "ESPN's ids for the players on the team's roster, bench and injured "
                        + "reserve included. Empty before the team has drafted anyone.")
        List<Long> playerIds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The same players as playerIds, in the same order, each with name, club, "
                        + "positions, today's slot and injury status.")
        List<LeagueRosterPlayer> players
) {
}
