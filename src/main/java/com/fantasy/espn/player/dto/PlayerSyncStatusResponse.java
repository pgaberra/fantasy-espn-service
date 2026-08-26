package com.fantasy.espn.player.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * When the cached pool was last written, and how many players it holds. Cheap enough to ask on
 * every read: a caller that only needs to know whether the pool has moved should not have to
 * fetch the pool to find out.
 */
public record PlayerSyncStatusResponse(
        @Schema(description = "When the pool was last refreshed from ESPN; absent while no sync "
                + "has succeeded yet")
        Instant syncedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Players stored")
        long players,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether a sync is running right now. A triggered sync is watched "
                        + "by polling this until it is false and syncedAt has moved.")
        boolean running) {
}
