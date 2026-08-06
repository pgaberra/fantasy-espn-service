package com.fantasy.espn.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** A team in a user's ESPN league. */
public record LeagueTeam(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "True for the authenticated user's own team (matched via their SWID; "
                        + "always false for public leagues read without cookies).") boolean mine
) {
}
