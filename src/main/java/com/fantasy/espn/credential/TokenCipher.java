package com.fantasy.espn.credential;

import com.fantasy.espn.config.EspnProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM encryption for stored ESPN cookies at rest. The key is a base64-encoded 256-bit
 * value supplied via {@code TOKEN_ENCRYPTION_KEY}; generate one with
 * {@code openssl rand -base64 32}. The stored value is base64(iv ‖ ciphertext+tag).
 *
 * <p>The key is checked when this bean is built, so the service refuses to start without a
 * usable one. It used to be checked on first use: a deployment with no key booted, reported
 * healthy, and failed only when a user saved cookies for a private league.
 */
@Component
public final class TokenCipher {

    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int KEY_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public TokenCipher(EspnProperties props) {
        this.key = parseKey(props.tokenEncryptionKey());
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv).put(ciphertext).array();
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt cookie", e);
        }
    }

    public String decrypt(String stored) {
        try {
            byte[] combined = Base64.getDecoder().decode(stored);
            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[GCM_IV_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt cookie", e);
        }
    }

    // No message here includes the value or the decoder's own message, which names the offending
    // character: a startup failure lands in the logs and Sentry, and the key must not.
    private static SecretKeySpec parseKey(String base64Key) {
        if (!StringUtils.hasText(base64Key)) {
            throw new IllegalStateException(
                    "espn.token-encryption-key (TOKEN_ENCRYPTION_KEY) must be set. Refusing to start "
                    + "without a key to encrypt stored ESPN cookies.");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException notBase64) {
            throw new IllegalStateException(
                    "TOKEN_ENCRYPTION_KEY is unset or not valid base64. Refusing to start.");
        }
        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalStateException("TOKEN_ENCRYPTION_KEY must decode to 32 bytes (256-bit), "
                    + "not " + keyBytes.length + ". Refusing to start.");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
