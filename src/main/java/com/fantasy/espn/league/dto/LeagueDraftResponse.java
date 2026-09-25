package com.fantasy.espn.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** An ESPN league's draft: its status, its teams and the picks made so far. */
public record LeagueDraftResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String leagueId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The season read, as ESPN's season id (the year the season ends in).",
                example = "2027")
        int season,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DraftStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "True for an auction draft, whose picks are bought rather than taken in turn.")
        boolean auction,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The league's teams in first-round draft order (`draftSettings.pickOrder`), "
                        + "then any team the order does not name, in ESPN's team order; `orderKnown` says "
                        + "whether every team was placed by the order.")
        List<LeagueDraftTeam> teams,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "True when `teams` is the draft order: ESPN's pick order names every team "
                        + "exactly once. False otherwise, when a team's place in the list says nothing "
                        + "about when it picks.")
        boolean orderKnown,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The picks made so far, by overall pick number.")
        List<LeagueDraftPick> picks
) {
}
