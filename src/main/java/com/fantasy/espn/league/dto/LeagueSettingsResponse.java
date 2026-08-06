package com.fantasy.espn.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record LeagueSettingsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String leagueId,
        String name,
        @Schema(description = "ESPN scoring type, e.g. H2H_POINTS, H2H_CATEGORY, ROTO.")
        String scoringType,
        @Schema(description = "Number of teams in the league.") Integer size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<StatCategory> statCategories,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<RosterSlot> rosterPositions
) {
}
