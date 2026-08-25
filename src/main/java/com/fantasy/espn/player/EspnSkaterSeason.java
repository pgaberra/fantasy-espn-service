package com.fantasy.espn.player;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One skater's totals for one season, cached from ESPN. {@code season} is the start year —
 * 2025 is the 2025-26 season.
 *
 * <p>The last three columns are the stats Yahoo does not report at all, which is why this
 * cache existed before it served the whole read model.
 */
@Entity
@Table(name = "espn_skater_seasons")
@IdClass(PlayerSeasonId.class)
public class EspnSkaterSeason {

    @Id
    public Long playerId;
    @Id
    public Integer season;

    public Integer gamesPlayed;
    public Integer goals;
    public Integer assists;
    public Integer points;
    public Integer plusMinus;
    public Integer pim;
    public Integer powerPlayGoals;
    public Integer powerPlayPoints;
    public Integer shorthandedGoals;
    public Integer shorthandedPoints;
    public Integer gameWinningGoals;
    public Integer shots;
    public Double shootingPctg;
    public String avgToi;
    public Double faceoffWinningPctg;
    public Integer hits;
    public Integer blockedShots;
    public Integer totalFaceoffWins;
    public Integer totalFaceoffLosses;
    public Integer hatTricks;
    public Integer shifts;
    public Integer timeOnIce;
    public Instant syncedAt;
}
