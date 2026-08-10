package com.fantasy.espn.credential;

import com.fantasy.espn.config.EspnProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenCipherTest {

    // base64 of 32 bytes ("0123456789abcdef0123456789abcdef").
    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private static TokenCipher cipher(String key) {
        return new TokenCipher(new EspnProperties(null, "fhl", 2026, 2026, key));
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

    @Test
    void fails_whenKeyMissing() {
        assertThatThrownBy(() -> cipher("").encrypt("x")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fails_whenKeyWrongLength() {
        assertThatThrownBy(() -> cipher("dG9vLXNob3J0").encrypt("x"))
                .isInstanceOf(IllegalStateException.class);
    }
}
