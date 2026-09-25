package com.fantasy.espn.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** A team in a league's draft. */
public record LeagueDraftTeam(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "ESPN's id for the team, unique within the league.", example = "3")
        int teamId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "True for the authenticated user's own team (matched via their SWID; "
                        + "always false for a league read without cookies).") boolean mine
) {
}
