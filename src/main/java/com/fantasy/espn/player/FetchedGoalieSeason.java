package com.fantasy.espn.player;

/** One goalie's season totals as read from ESPN. {@code season} is the start year. */
public record FetchedGoalieSeason(
        int season,
        Integer gamesPlayed,
        Integer gamesStarted,
        Integer wins,
        Integer losses,
        Integer shutouts,
        Integer shotsAgainst,
        Integer saves,
        Integer goalsAgainst,
        Double goalsAgainstAvg,
        Double savePctg,
        Integer hatTricks,
        Integer overtimeLosses,
        Integer timeOnIce) {
}
