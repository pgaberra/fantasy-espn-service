package com.fantasy.espn.player;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One goalie's totals for one season, cached from ESPN. {@code season} is the start year —
 * 2025 is the 2025-26 season.
 *
 * <p>The last three columns are the stats Yahoo does not report at all, which is why this
 * cache existed before it served the whole read model.
 */
@Entity
@Table(name = "espn_goalie_seasons")
@IdClass(PlayerSeasonId.class)
public class EspnGoalieSeason {

    @Id
    public Long playerId;
    @Id
    public Integer season;

    public Integer gamesPlayed;
    public Integer gamesStarted;
    public Integer wins;
    public Integer losses;
    public Integer shutouts;
    public Integer shotsAgainst;
    public Integer saves;
    public Integer goalsAgainst;
    public Double goalsAgainstAvg;
    public Double savePctg;
    public Integer hatTricks;
    public Integer overtimeLosses;
    public Integer timeOnIce;
    public Instant syncedAt;
}
