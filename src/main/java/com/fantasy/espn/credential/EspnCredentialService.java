package com.fantasy.espn.credential;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Stores and retrieves a user's ESPN cookies (espn_s2 + SWID), encrypted at rest. Used to
 * read private leagues; public leagues need no credentials.
 *
 * The cookies leave this service two ways: as the {@link EspnCookies} handed to the league
 * reader, and through the controller's {@code /values} read, which exists so a user can be shown
 * their own saved connection. They are never logged.
 */
@Service
public class EspnCredentialService {

    private final EspnCredentialRepository repository;
    private final TokenCipher cipher;

    public EspnCredentialService(EspnCredentialRepository repository, TokenCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    @Transactional
    public void save(String appUserId, String espnS2, String swid) {
        EspnCredential credential = repository.findById(appUserId).orElseGet(() -> {
            EspnCredential created = new EspnCredential();
            created.setAppUserId(appUserId);
            created.setCreatedAt(Instant.now());
            return created;
        });
        credential.setEspnS2Enc(cipher.encrypt(espnS2));
        credential.setSwidEnc(cipher.encrypt(swid));
        credential.setUpdatedAt(Instant.now());
        repository.save(credential);
    }

    @Transactional
    public void delete(String appUserId) {
        repository.deleteById(appUserId);
    }

    @Transactional(readOnly = true)
    public boolean hasCredentials(String appUserId) {
        return repository.existsById(appUserId);
    }

    /** The user's decrypted cookies, if they have connected ESPN (empty for public-only users). */
    @Transactional(readOnly = true)
    public Optional<EspnCookies> find(String appUserId) {
        return repository.findById(appUserId)
                .map(c -> new EspnCookies(cipher.decrypt(c.getEspnS2Enc()), cipher.decrypt(c.getSwidEnc())));
    }
}
