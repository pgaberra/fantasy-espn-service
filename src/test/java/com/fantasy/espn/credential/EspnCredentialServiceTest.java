package com.fantasy.espn.credential;

import com.fantasy.espn.config.EspnProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EspnCredentialServiceTest {

    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private EspnCredentialRepository repository;
    private TokenCipher cipher;
    private EspnCredentialService service;

    @BeforeEach
    void setUp() {
        repository = mock(EspnCredentialRepository.class);
        cipher = new TokenCipher(new EspnProperties(null, "fhl", 2026, KEY));
        service = new EspnCredentialService(repository, cipher);
    }

    @Test
    void save_storesCookiesEncrypted() {
        when(repository.findById("u1")).thenReturn(Optional.empty());

        service.save("u1", "s2-value", "{SWID-1}");

        ArgumentCaptor<EspnCredential> saved = ArgumentCaptor.forClass(EspnCredential.class);
        verify(repository).save(saved.capture());
        EspnCredential entity = saved.getValue();
        assertThat(entity.getAppUserId()).isEqualTo("u1");
        assertThat(entity.getEspnS2Enc()).isNotEqualTo("s2-value");
        assertThat(entity.getSwidEnc()).isNotEqualTo("{SWID-1}");
        assertThat(cipher.decrypt(entity.getEspnS2Enc())).isEqualTo("s2-value");
        assertThat(cipher.decrypt(entity.getSwidEnc())).isEqualTo("{SWID-1}");
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

    @Test
    void find_returnsDecryptedCookies() {
        EspnCredential stored = new EspnCredential();
        stored.setAppUserId("u2");
        stored.setEspnS2Enc(cipher.encrypt("s2"));
        stored.setSwidEnc(cipher.encrypt("{W}"));
        when(repository.findById("u2")).thenReturn(Optional.of(stored));

        assertThat(service.find("u2")).contains(new EspnCookies("s2", "{W}"));
    }

    @Test
    void find_isEmpty_whenNoCredentials() {
        when(repository.findById("nobody")).thenReturn(Optional.empty());

        assertThat(service.find("nobody")).isEmpty();
    }

    @Test
    void hasCredentials_delegatesToRepository() {
        when(repository.existsById("u3")).thenReturn(true);

        assertThat(service.hasCredentials("u3")).isTrue();
    }

    @Test
    void delete_delegatesToRepository() {
        service.delete("u4");

        verify(repository).deleteById("u4");
    }
}
