package com.fantasy.espn.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** An ESPN league's teams as they stand today, each with the players on its roster. */
public record LeagueRostersResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String leagueId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The season read, as ESPN's season id (the year the season ends in).",
                example = "2027")
        int season,
        @Schema(description = "ESPN's name for the league, or null where ESPN gave none.")
        String leagueName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Where the league's draft has got to. A player drafted is on his team's "
                        + "roster at once, so a draft in progress shows its picks so far.")
        DraftStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The league's teams in ESPN's team order.")
        List<LeagueRosterTeam> teams
) {
}
