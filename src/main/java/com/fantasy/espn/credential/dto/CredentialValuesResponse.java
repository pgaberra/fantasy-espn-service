package com.fantasy.espn.credential.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The user's stored ESPN cookies, decrypted, so the web app can show them their own connection
 * filled in rather than a pair of empty boxes.
 *
 * Every other caller wants {@link CredentialStatusResponse}: it answers whether a pair exists
 * without moving the pair itself.
 */
public record CredentialValuesResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The stored espn_s2 cookie.")
        String espnS2,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The stored SWID cookie.")
        String swid
) {
}
