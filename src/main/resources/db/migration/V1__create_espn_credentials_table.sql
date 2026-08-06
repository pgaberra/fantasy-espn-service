-- Per-user ESPN session cookies, keyed by the fantasy app's user id (the JWT subject
-- the BFF forwards). ESPN has no public OAuth: private leagues are read using the user's
-- espn_s2 + SWID browser cookies. They are stored AES-GCM encrypted, so the columns hold
-- base64 ciphertext, not the raw cookie values.
CREATE TABLE espn_credentials (
    app_user_id   VARCHAR(64)   PRIMARY KEY,   -- fantasy app user id
    espn_s2_enc   VARCHAR(2048) NOT NULL,      -- encrypted espn_s2 cookie
    swid_enc      VARCHAR(2048) NOT NULL,      -- encrypted SWID cookie
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL
);
