package com.fantasy.espn.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** A player on a team's roster today, with the slot the manager has him in. */
public record LeagueRosterPlayer(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "ESPN's player id")
        long espnId,
        @Schema(description = "His full name; absent when ESPN sends no player with the entry")
        String fullName,
        @Schema(description = "The NHL club ESPN has him on, in ESPN's own abbreviation (TB, LA, NJ, SJ). "
                + "Absent for a player ESPN lists on no club")
        String teamAbbrev,
        Integer uniformNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean goalie,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Every position he may be started at in this league; empty when ESPN sends "
                        + "no player with the entry")
        List<String> eligiblePositions,
        @Schema(description = "The slot he sits in today (C, LW, RW, F, D, G, Util, BN, IR); absent when "
                + "ESPN does not say", example = "BN")
        String lineupSlot,
        @Schema(description = "ESPN's injury status (DAY_TO_DAY, OUT, INJURY_RESERVE, SUSPENSION, …); "
                + "absent for a healthy player", example = "DAY_TO_DAY")
        String injuryStatus) {}
