package com.fantasy.espn.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A scored stat in the league, from ESPN's {@code scoringItems}. {@code statId} is ESPN's
 * numeric stat code (the BFF maps it to a projection stat). {@code pointValue} is the per-stat
 * points modifier for points leagues (absent for category leagues, where the stat's presence
 * alone marks it an active category). {@code name} is a best-effort human abbreviation.
 */
public record StatCategory(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int statId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        Double pointValue
) {
}
