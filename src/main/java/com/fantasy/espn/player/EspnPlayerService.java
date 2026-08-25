package com.fantasy.espn.player;

import com.fantasy.espn.config.EspnProperties;
import com.fantasy.espn.player.dto.GoalieResponse;
import com.fantasy.espn.player.dto.PlayerStatLine;
import com.fantasy.espn.player.dto.PlayerStatsResponse;
import com.fantasy.espn.player.dto.SkaterResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serves the cached player pool as it is now, with the stat line of whichever season the
 * caller asks for. A player with no row for that season comes back with the identity and no
 * numbers, which is the honest answer for a rookie, a call-up, or anyone who did not play that
 * year.
 */
@Service
public class EspnPlayerService {

    private final EspnPlayerRepository playerRepository;
    private final EspnSkaterSeasonRepository skaterSeasonRepository;
    private final EspnGoalieSeasonRepository goalieSeasonRepository;
    private final int referenceSeason;

    public EspnPlayerService(EspnPlayerRepository playerRepository,
                             EspnSkaterSeasonRepository skaterSeasonRepository,
                             EspnGoalieSeasonRepository goalieSeasonRepository,
                             EspnProperties props) {
        this.playerRepository = playerRepository;
        this.skaterSeasonRepository = skaterSeasonRepository;
        this.goalieSeasonRepository = goalieSeasonRepository;
        this.referenceSeason = props.playerReferenceSeason();
    }

    @Transactional(readOnly = true)
    public List<SkaterResponse> getSkaters(int season) {
        Map<Long, EspnSkaterSeason> stats = bySkaterId(season);
        return playerRepository
                .findAllByGoalieAndActiveIsTrueOrderByLastNameAscFirstNameAsc(false).stream()
                .map(player -> toSkaterResponse(player, stats.get(player.id)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GoalieResponse> getGoalies(int season) {
        Map<Long, EspnGoalieSeason> stats = byGoalieId(season);
        return playerRepository
                .findAllByGoalieAndActiveIsTrueOrderByLastNameAscFirstNameAsc(true).stream()
                .map(player -> toGoalieResponse(player, stats.get(player.id)))
                .toList();
    }

    /**
     * The stats Yahoo does not report, for the reference season, keyed by ESPN's player id. The
     * BFF matches these onto its own player list by name, so only players with a stat line to
     * contribute are returned.
     */
    @Transactional(readOnly = true)
    public PlayerStatsResponse players() {
        Map<Long, EspnSkaterSeason> skaterStats = bySkaterId(referenceSeason);
        Map<Long, EspnGoalieSeason> goalieStats = byGoalieId(referenceSeason);
        List<PlayerStatLine> lines = new ArrayList<>();
        Instant syncedAt = null;
        for (EspnPlayer player : playerRepository.findAll()) {
            EspnSkaterSeason skater = skaterStats.get(player.id);
            EspnGoalieSeason goalie = goalieStats.get(player.id);
            if (skater != null) {
                lines.add(new PlayerStatLine(player.id, player.fullName, player.position,
                        player.sweaterNumber, skater.gamesPlayed, skater.hatTricks, skater.shifts,
                        null, skater.timeOnIce));
                syncedAt = latest(syncedAt, skater.syncedAt);
            } else if (goalie != null) {
                lines.add(new PlayerStatLine(player.id, player.fullName, player.position,
                        player.sweaterNumber, goalie.gamesPlayed, goalie.hatTricks, null,
                        goalie.overtimeLosses, goalie.timeOnIce));
                syncedAt = latest(syncedAt, goalie.syncedAt);
            }
        }
        return new PlayerStatsResponse(lines, syncedAt);
    }

    private Map<Long, EspnSkaterSeason> bySkaterId(int season) {
        Map<Long, EspnSkaterSeason> stats = new HashMap<>();
        for (EspnSkaterSeason line : skaterSeasonRepository.findAllBySeason(season)) {
            stats.put(line.playerId, line);
        }
        return stats;
    }

    private Map<Long, EspnGoalieSeason> byGoalieId(int season) {
        Map<Long, EspnGoalieSeason> stats = new HashMap<>();
        for (EspnGoalieSeason line : goalieSeasonRepository.findAllBySeason(season)) {
            stats.put(line.playerId, line);
        }
        return stats;
    }

    private static Instant latest(Instant current, Instant candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.isAfter(current) ? candidate : current;
    }

    private static SkaterResponse toSkaterResponse(EspnPlayer player, EspnSkaterSeason line) {
        EspnSkaterSeason stats = line == null ? new EspnSkaterSeason() : line;
        return new SkaterResponse(
                player.id, player.firstName, player.lastName, player.position,
                eligiblePositions(player), player.sweaterNumber, player.teamAbbrev, player.headshot,
                stats.gamesPlayed, stats.goals, stats.assists, stats.points, stats.plusMinus,
                stats.pim, stats.powerPlayGoals, stats.powerPlayPoints, stats.shorthandedGoals,
                stats.shorthandedPoints, stats.gameWinningGoals, stats.shots, stats.shootingPctg,
                stats.avgToi, stats.faceoffWinningPctg, stats.hits, stats.blockedShots,
                stats.totalFaceoffWins, stats.totalFaceoffLosses, stats.hatTricks, stats.shifts,
                stats.timeOnIce);
    }

    private static GoalieResponse toGoalieResponse(EspnPlayer player, EspnGoalieSeason line) {
        EspnGoalieSeason stats = line == null ? new EspnGoalieSeason() : line;
        return new GoalieResponse(
                player.id, player.firstName, player.lastName, player.position,
                eligiblePositions(player), player.sweaterNumber, player.teamAbbrev, player.headshot,
                stats.gamesPlayed, stats.gamesStarted, stats.wins, stats.losses, stats.shutouts,
                stats.shotsAgainst, stats.saves, stats.goalsAgainst, stats.goalsAgainstAvg,
                stats.savePctg, stats.overtimeLosses, stats.timeOnIce);
    }

    /** Stored comma-joined; the primary position stands in if a row ever lost them. */
    private static List<String> eligiblePositions(EspnPlayer player) {
        if (player.eligiblePositions == null || player.eligiblePositions.isBlank()) {
            return List.of(player.position);
        }
        return Arrays.stream(player.eligiblePositions.split(","))
                .map(String::trim)
                .filter(position -> !position.isEmpty())
                .toList();
    }
}
