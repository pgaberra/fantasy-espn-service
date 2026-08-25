package com.fantasy.espn.player;

/** One skater's season totals as read from ESPN. {@code season} is the start year. */
public record FetchedSkaterSeason(
        int season,
        Integer gamesPlayed,
        Integer goals,
        Integer assists,
        Integer points,
        Integer plusMinus,
        Integer pim,
        Integer powerPlayGoals,
        Integer powerPlayPoints,
        Integer shorthandedGoals,
        Integer shorthandedPoints,
        Integer gameWinningGoals,
        Integer shots,
        Double shootingPctg,
        String avgToi,
        Double faceoffWinningPctg,
        Integer hits,
        Integer blockedShots,
        Integer totalFaceoffWins,
        Integer totalFaceoffLosses,
        Integer hatTricks,
        Integer shifts,
        Integer timeOnIce) {
}
