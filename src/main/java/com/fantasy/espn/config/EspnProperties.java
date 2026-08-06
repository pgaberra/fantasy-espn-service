package com.fantasy.espn.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the ESPN Fantasy v3 API.
 *
 * ESPN has no public OAuth for fantasy, so there are no client credentials here. Public
 * leagues are read with only a league id; private leagues additionally need the user's
 * espn_s2 + SWID cookies, which are stored encrypted with {@code tokenEncryptionKey}. That
 * key comes from the environment and defaults to empty so the app still boots for tests/CI
 * — the credential endpoints simply fail at call time when it is unset.
 */
@ConfigurationProperties(prefix = "espn")
public record EspnProperties(
        // ESPN Fantasy read API base (lm-api-reads.fantasy.espn.com).
        String apiBaseUrl,
        // Fantasy game code (fhl = NHL hockey).
        String gameKey,
        // Base64-encoded 256-bit AES key used to encrypt stored espn_s2 / SWID cookies.
        String tokenEncryptionKey
) {
}
