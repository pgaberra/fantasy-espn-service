package com.fantasy.espn.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** The teams in a user's ESPN league, in draft order when the league has one, otherwise in ESPN's team order. */
public record LeagueTeamsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<LeagueTeam> teams
) {
}
