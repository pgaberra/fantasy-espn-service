package com.fantasy.espn.player;

import java.io.Serializable;
import java.util.Objects;

/** Composite key of the season stat lines: one row per player and season. */
public class PlayerSeasonId implements Serializable {

    private static final long serialVersionUID = 1L;

    public Long playerId;
    public Integer season;

    public PlayerSeasonId() {
    }

    public PlayerSeasonId(Long playerId, Integer season) {
        this.playerId = playerId;
        this.season = season;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlayerSeasonId that)) {
            return false;
        }
        return Objects.equals(playerId, that.playerId) && Objects.equals(season, that.season);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId, season);
    }
}
