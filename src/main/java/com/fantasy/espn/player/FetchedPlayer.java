package com.fantasy.espn.player;

import java.util.List;

/**
 * One player as ESPN's public player endpoint describes them, with every season stat line the
 * payload carried. A goalie fills {@code goalieSeasons} and a skater {@code skaterSeasons};
 * the other list is always empty, because ESPN reports a different stat vocabulary for each.
 *
 * <p>Seasons are start years — 2025 is the 2025-26 season. ESPN keys a season by the year it
 * ends in, and that convention stops here.
 */
public record FetchedPlayer(
        long id,
        String firstName,
        String lastName,
        String fullName,
        String position,
        List<String> eligiblePositions,
        Integer sweaterNumber,
        String teamAbbrev,
        String headshot,
        boolean active,
        boolean goalie,
        List<FetchedSkaterSeason> skaterSeasons,
        List<FetchedGoalieSeason> goalieSeasons) {

    public int gamesPlayedIn(int season) {
        for (FetchedSkaterSeason line : skaterSeasons) {
            if (line.season() == season) {
                return line.gamesPlayed() == null ? 0 : line.gamesPlayed();
            }
        }
        for (FetchedGoalieSeason line : goalieSeasons) {
            if (line.season() == season) {
                return line.gamesPlayed() == null ? 0 : line.gamesPlayed();
            }
        }
        return 0;
    }

    public boolean hasSeason(int season) {
        return skaterSeasons.stream().anyMatch(line -> line.season() == season)
                || goalieSeasons.stream().anyMatch(line -> line.season() == season);
    }
}
