package com.fantasy.espn.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the ESPN Fantasy v3 API.
 *
 * ESPN has no public OAuth for fantasy, so there are no client credentials here. Public
 * leagues are read with only a league id; private leagues additionally need the user's
 * espn_s2 + SWID cookies, which are stored encrypted with {@code tokenEncryptionKey}. That
 * key comes only from the environment, with no default: {@code TokenCipher} refuses to start
 * the service when it is missing or does not decode to 32 bytes. Tests supply their own.
 */
@ConfigurationProperties(prefix = "espn")
public record EspnProperties(
        // ESPN Fantasy read API base (lm-api-reads.fantasy.espn.com).
        String apiBaseUrl,
        // Fantasy game code (fhl = NHL hockey).
        String gameKey,
        // The season leagues are read for, as ESPN's season id. ESPN keys a season by the year
        // it ends in: 2027 is the 2026-27 season. Users never pick one. When a league isn't
        // found for it — e.g. the user hasn't renewed yet — the reader falls back to the season
        // before it.
        Integer season,
        // The season whose player pool is cached, as a *start* year: 2026 is the 2026-27
        // season. ESPN's end-year convention stops at the player client, so every season below
        // this line reads the way the rest of the app says it.
        Integer playerPoolSeason,
        // The season a cached stat line is a reference for: the last season actually played,
        // which is the one projections are seeded from. It trails the pool season for most of
        // the year, because ESPN opens a season months before it is played and reports all-zero
        // totals for it in the meantime — so this is also the season a sync must find real
        // numbers for before it is allowed to replace anything.
        Integer playerReferenceSeason,
        // Base64-encoded 256-bit AES key used to encrypt stored espn_s2 / SWID cookies.
        String tokenEncryptionKey
) {
}
