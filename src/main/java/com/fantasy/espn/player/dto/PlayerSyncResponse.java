package com.fantasy.espn.player.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record PlayerSyncResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Stat lines stored")
        int players,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant syncedAt) {
}
