package com.fantasy.espn.credential;

import com.fantasy.espn.config.EspnProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenCipherTest {

    // base64 of 32 bytes ("0123456789abcdef0123456789abcdef").
    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private static TokenCipher cipher(String key) {
        return new TokenCipher(new EspnProperties(null, "fhl", 2027, 2026, 2025, key));
    }

    @Test
    void encryptThenDecrypt_roundTrips() {
        TokenCipher cipher = cipher(KEY);
        String plaintext = "AEAAAABBBBCCCCdddd-espn_s2-value";

        String encrypted = cipher.encrypt(plaintext);

        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test
    void encrypt_producesDifferentCiphertextEachTime() {
        TokenCipher cipher = cipher(KEY);

        // A fresh random IV per call means the same plaintext encrypts to different ciphertext.
        assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void refusesToBuild_whenKeyMissing(String key) {
        // At construction, so a deployment without a key never starts, rather than starting
        // healthy and failing the first user who saves cookies.
        assertThatThrownBy(() -> cipher(key))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOKEN_ENCRYPTION_KEY");
    }

    @Test
    void refusesToBuild_whenKeyWrongLength() {
        assertThatThrownBy(() -> cipher("dG9vLXNob3J0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void refusesToBuild_whenKeyIsNotBase64_withoutRepeatingIt() {
        // Also what an unresolved ${TOKEN_ENCRYPTION_KEY} placeholder looks like once bound.
        String notBase64 = "${TOKEN_ENCRYPTION_KEY}";

        assertThatThrownBy(() -> cipher(notBase64))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(notBase64)
                .hasNoCause();
    }
}
