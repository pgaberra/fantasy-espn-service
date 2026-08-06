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
 * The key is validated lazily (on first use), not at construction, so the app still
 * boots for tests/CI when the key is unset.
 */
@Component
public class TokenCipher {

    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String base64Key;

    public TokenCipher(EspnProperties props) {
        this.base64Key = props.tokenEncryptionKey();
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
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
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt cookie", e);
        }
    }

    private SecretKeySpec key() {
        if (!StringUtils.hasText(base64Key)) {
            throw new IllegalStateException("TOKEN_ENCRYPTION_KEY is not configured");
        }
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalStateException("TOKEN_ENCRYPTION_KEY must decode to 32 bytes (256-bit)");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
