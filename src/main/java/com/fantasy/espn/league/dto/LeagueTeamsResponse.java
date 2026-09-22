package com.fantasy.espn.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The teams in a user's ESPN league, in draft order when ESPN gives one, otherwise in ESPN's own
 * team order. The list order alone never says where the user picks — only {@code draftPosition}
 * does, and it is null whenever we do not know.
 */
public record LeagueTeamsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<LeagueTeam> teams,

        @Schema(description = "The signed-in manager's own seat in the draft order, 1-based, or null "
                + "when we do not know it: ESPN gave no pick order, or no team could be matched to "
                + "the user. Null is meaningful — a caller must ask the user rather than guess from "
                + "the position of their team in the list.",
                example = "12") Integer draftPosition
) {
}
