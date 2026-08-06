package com.fantasy.espn.credential.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record CredentialStatusResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the user has stored ESPN cookies (for private leagues).")
        boolean hasCredentials
) {
}
