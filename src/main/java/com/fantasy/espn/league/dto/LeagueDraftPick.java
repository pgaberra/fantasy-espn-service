package com.fantasy.espn.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** One pick made in a league's draft. */
public record LeagueDraftPick(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Overall pick number, from 1.") int pick,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int round,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The team that made the pick.")
        int teamId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "ESPN's player id, the id this service's player read model uses.", example = "4697382")
        long playerId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "True for a keeper: a player the team kept, placed in the draft rather than taken.")
        boolean keeper
) {
}
