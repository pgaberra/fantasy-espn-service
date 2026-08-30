package com.fantasy.espn.player.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A sync has been started, not finished. It takes minutes; watch it with
 * {@code GET /api/v1/espn/players/sync/latest} until {@code running} is false and
 * {@code syncedAt} has moved.
 */
public record SyncAcceptedResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "started, or running when one was already under way")
        String status) {
}
