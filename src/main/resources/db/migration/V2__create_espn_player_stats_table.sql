-- Season stat lines ESPN reports for its own player universe, cached from ESPN's public
-- player endpoint. Only the stats Yahoo does not report live here — the BFF joins them onto
-- the Yahoo-sourced player read model by name + position, so identity columns exist purely
-- to make that join possible. The id is ESPN's player id.
CREATE TABLE espn_player_stats (
    id               BIGINT       PRIMARY KEY,
    full_name        VARCHAR(120) NOT NULL,
    position         VARCHAR(4)   NOT NULL,   -- C, LW, RW, D, G
    games_played     INT,
    hat_tricks       INT,
    shifts           INT,
    overtime_losses  INT,
    time_on_ice      INT,                     -- season total, seconds
    synced_at        TIMESTAMPTZ  NOT NULL
);
