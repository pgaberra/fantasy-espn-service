package com.fantasy.espn.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A roster position slot and how many the league carries, e.g. {@code C × 2}, {@code BN × 4}.
 * {@code position} is ESPN's lineup-slot code (C, LW, RW, F, D, G, Util, BN, IR).
 */
public record RosterSlot(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String position,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int count
) {
}
