package com.fantasy.espn.player;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A player in the cached pool: identity and ESPN's fantasy eligibility, keyed by ESPN's own
 * player id. The numbers live in {@link EspnSkaterSeason} / {@link EspnGoalieSeason}, because
 * the pool turns over between seasons while a finished season's totals never change again.
 *
 * <p>Field access (public fields) keeps these wide, boilerplate-free entities readable;
 * Hibernate maps camelCase fields to snake_case columns.
 */
@Entity
@Table(name = "espn_players")
public class EspnPlayer {

    @Id
    public Long id;
    public String firstName;
    public String lastName;
    public String fullName;
    public String position;
    /** ESPN eligible positions, comma-joined (e.g. "C,LW"). Never blank. */
    public String eligiblePositions;
    public Integer sweaterNumber;
    public String teamAbbrev;
    public String headshot;
    public Boolean goalie;
    public Boolean active;
    public Instant syncedAt;
}
