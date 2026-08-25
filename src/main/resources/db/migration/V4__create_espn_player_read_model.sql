-- The full player read model, sourced from ESPN's public player endpoint: the pool as it is
-- now, plus one stat line per player and season.
--
-- It replaces espn_player_stats, which held five columns for the stats Yahoo does not report.
-- The same fetch already carries every stat, the eligible positions and the identity, so the
-- narrow table was one read of a 33 MB payload spent on a fraction of what it contained. The
-- old endpoint is still served, now from these tables.
--
-- Identity and numbers are kept apart because they have different lifetimes: the pool turns
-- over between seasons while a finished season's numbers never change again.
--
-- `season` is the start year — 2025 is the 2025-26 season. ESPN keys a season by the year it
-- ends in; that convention is converted away at the edge so this service speaks the same
-- season language as the rest of the app.

CREATE TABLE espn_players (
    id                 BIGINT       PRIMARY KEY,
    first_name         VARCHAR(100) NOT NULL,
    last_name          VARCHAR(100) NOT NULL,
    full_name          VARCHAR(120) NOT NULL,
    position           VARCHAR(4)   NOT NULL,   -- primary position: C, LW, RW, D, G
    -- ESPN eligible positions, comma-joined (e.g. "C,LW"). Never empty: a player with no
    -- usable slot falls back to the primary position.
    eligible_positions VARCHAR(32)  NOT NULL,
    sweater_number     INT,
    team_abbrev        VARCHAR(10),
    headshot           TEXT,
    goalie             BOOLEAN      NOT NULL,
    -- Whether ESPN still lists the player as active. Retired players are kept while they have
    -- a stat line, so a season already played stays readable; only the pool endpoints filter.
    active             BOOLEAN      NOT NULL,
    synced_at          TIMESTAMPTZ  NOT NULL
);

CREATE TABLE espn_skater_seasons (
    player_id            BIGINT      NOT NULL REFERENCES espn_players (id) ON DELETE CASCADE,
    season               INT         NOT NULL,
    games_played         INT,
    goals                INT,
    assists              INT,
    points               INT,
    plus_minus           INT,
    pim                  INT,
    power_play_goals     INT,
    power_play_points    INT,
    shorthanded_goals    INT,
    shorthanded_points   INT,
    game_winning_goals   INT,
    shots                INT,
    shooting_pctg        DOUBLE PRECISION,
    avg_toi              VARCHAR(8),
    faceoff_winning_pctg DOUBLE PRECISION,
    hits                 INT,
    blocked_shots        INT,
    total_faceoff_wins   INT,
    total_faceoff_losses INT,
    -- Stats Yahoo does not report at all.
    hat_tricks           INT,
    shifts               INT,
    time_on_ice          INT,
    synced_at            TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (player_id, season)
);

CREATE TABLE espn_goalie_seasons (
    player_id          BIGINT      NOT NULL REFERENCES espn_players (id) ON DELETE CASCADE,
    season             INT         NOT NULL,
    games_played       INT,
    games_started      INT,
    wins               INT,
    losses             INT,
    shutouts           INT,
    shots_against      INT,
    saves              INT,
    goals_against      INT,
    goals_against_avg  DOUBLE PRECISION,
    save_pctg          DOUBLE PRECISION,
    -- Stats Yahoo does not report at all.
    hat_tricks         INT,
    overtime_losses    INT,
    time_on_ice        INT,
    synced_at          TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (player_id, season)
);

CREATE INDEX idx_espn_skater_seasons_season ON espn_skater_seasons (season);
CREATE INDEX idx_espn_goalie_seasons_season ON espn_goalie_seasons (season);

DROP TABLE espn_player_stats;
