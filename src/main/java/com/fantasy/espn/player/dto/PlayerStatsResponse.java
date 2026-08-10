package com.fantasy.espn.player.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record PlayerStatsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PlayerStatLine> players,
        @Schema(description = "When the cached stat lines were last refreshed from ESPN; "
                + "absent while no sync has succeeded yet")
        Instant syncedAt) {
}
