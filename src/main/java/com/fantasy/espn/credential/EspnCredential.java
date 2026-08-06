package com.fantasy.espn.credential;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A user's stored ESPN session cookies, keyed by the fantasy app's user id (the JWT subject
 * the BFF forwards). The espn_s2 and SWID cookies are stored encrypted (see {@link TokenCipher}).
 */
@Entity
@Table(name = "espn_credentials")
public class EspnCredential {

    @Id
    @Column(name = "app_user_id", nullable = false, updatable = false)
    private String appUserId;

    @Column(name = "espn_s2_enc", nullable = false, length = 2048)
    private String espnS2Enc;

    @Column(name = "swid_enc", nullable = false, length = 2048)
    private String swidEnc;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public EspnCredential() {
    }

    public String getAppUserId() {
        return appUserId;
    }

    public void setAppUserId(String appUserId) {
        this.appUserId = appUserId;
    }

    public String getEspnS2Enc() {
        return espnS2Enc;
    }

    public void setEspnS2Enc(String espnS2Enc) {
        this.espnS2Enc = espnS2Enc;
    }

    public String getSwidEnc() {
        return swidEnc;
    }

    public void setSwidEnc(String swidEnc) {
        this.swidEnc = swidEnc;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
