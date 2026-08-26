package com.fantasy.espn.player;

import com.fantasy.espn.config.EspnProperties;
import com.fantasy.espn.player.dto.PlayerSyncResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Refreshes the cached player read model from ESPN's public player endpoint.
 *
 * <p>One fetch carries the whole pool: identity, fantasy eligibility and the season totals for
 * the season being played and the one before it. It is the app's player source now that
 * Yahoo's game-wide player collection is refused.
 */
@Service
public class EspnPlayerSyncService {

    private static final Logger log = LoggerFactory.getLogger(EspnPlayerSyncService.class);

    /** Roughly a thousand players record a stat line in a season; 50 is ESPN's page size. */
    private static final int MINIMUM_CREDIBLE_PLAYERS = 200;

    private final EspnPlayerClient client;
    private final EspnHeadshotVerifier headshotVerifier;
    private final EspnPlayerRepository playerRepository;
    private final EspnSkaterSeasonRepository skaterSeasonRepository;
    private final EspnGoalieSeasonRepository goalieSeasonRepository;
    private final TransactionTemplate transactionTemplate;
    private final int poolSeason;
    private final int referenceSeason;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public EspnPlayerSyncService(EspnPlayerClient client,
                                 EspnHeadshotVerifier headshotVerifier,
                                 EspnPlayerRepository playerRepository,
                                 EspnSkaterSeasonRepository skaterSeasonRepository,
                                 EspnGoalieSeasonRepository goalieSeasonRepository,
                                 TransactionTemplate transactionTemplate,
                                 EspnProperties props) {
        this.client = client;
        this.headshotVerifier = headshotVerifier;
        this.playerRepository = playerRepository;
        this.skaterSeasonRepository = skaterSeasonRepository;
        this.goalieSeasonRepository = goalieSeasonRepository;
        this.transactionTemplate = transactionTemplate;
        this.poolSeason = props.playerPoolSeason();
        this.referenceSeason = props.playerReferenceSeason();
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * Starts a sync and returns as soon as it is under way. It fetches tens of megabytes from
     * ESPN, parses the lot and asks the image CDN about every player — minutes of work, which
     * is far too long to hold an HTTP request open across every hop between here and the
     * caller. {@code GET /api/v1/espn/players/sync/latest} is how it is watched.
     *
     * <p>The flag is claimed here rather than inside the thread, so a caller that is told the
     * sync started can rely on it having started, and a second caller is refused rather than
     * queued. Already running is an ordinary answer, not a fault, so it comes back as
     * {@code false} rather than as an exception.
     *
     * @return whether this call is the one that started it
     */
    public boolean startAsync() {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        Thread.ofVirtual().name("espn-player-sync").start(() -> {
            try {
                runSync();
            } catch (Exception e) {
                // Background work never reaches the @RestControllerAdvice, so it logs its own.
                log.error("ESPN player sync failed", e);
            } finally {
                running.set(false);
            }
        });
        return true;
    }

    /** Runs a sync and waits for it. For the scheduler, which has nobody to answer to. */
    public PlayerSyncResponse sync() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A player sync is already running");
        }
        try {
            return runSync();
        } finally {
            running.set(false);
        }
    }

    private PlayerSyncResponse runSync() {
        List<FetchedPlayer> fetched = client.fetchPlayers(poolSeason);
        if (fetched.isEmpty()) {
            // Never wipe the read model on an empty fetch — ESPN is the only source.
            throw new IllegalStateException("ESPN returned no players; preserving existing data");
        }
        if (fetched.size() < MINIMUM_CREDIBLE_PLAYERS) {
            // ESPN pages the player endpoint at 50 and says nothing about the rest, so a
            // dropped request header shows up as a plausible-looking short list rather than as
            // an error. A real season holds roughly a thousand players.
            throw new IllegalStateException(
                    "ESPN returned only " + fetched.size() + " players, far fewer than a season "
                            + "holds; preserving existing data");
        }
        Set<Integer> playedSeasons = playedSeasons(fetched);
        if (!playedSeasons.contains(referenceSeason)) {
            // ESPN answers for a season that hasn't been played yet with a full set of all-zero
            // rows rather than nothing, so an off-by-one season would quietly replace every stat
            // line with zeroes. Refuse, and keep what we have.
            throw new IllegalStateException(
                    "ESPN reported no games played for season " + referenceSeason
                            + "; it has probably not been played yet. Preserving existing data");
        }

        // Only after the guards: there is no point asking the CDN about a fetch we are going
        // to refuse, and no point checking the duplicates we are about to drop.
        List<FetchedPlayer> pool = headshotVerifier.withVerifiedHeadshots(dedupe(fetched)).stream()
                // A player ESPN no longer lists as active is kept while a stored season still
                // has their numbers: someone who retired after the last season played is gone
                // from the pool but their stat line is still what a projection references.
                .filter(player -> player.active() || playedSeasons.stream().anyMatch(player::hasSeason))
                .toList();

        Instant now = Instant.now();
        List<EspnPlayer> players = new ArrayList<>();
        List<EspnSkaterSeason> skaterSeasons = new ArrayList<>();
        List<EspnGoalieSeason> goalieSeasons = new ArrayList<>();
        for (FetchedPlayer player : pool) {
            players.add(toEntity(player, now));
            for (FetchedSkaterSeason line : player.skaterSeasons()) {
                if (playedSeasons.contains(line.season())) {
                    skaterSeasons.add(toEntity(player.id(), line, now));
                }
            }
            for (FetchedGoalieSeason line : player.goalieSeasons()) {
                if (playedSeasons.contains(line.season())) {
                    goalieSeasons.add(toEntity(player.id(), line, now));
                }
            }
        }

        // Replace in one transaction so a failure mid-write can't leave the read model empty.
        transactionTemplate.executeWithoutResult(status -> {
            skaterSeasonRepository.deleteAllInBatch();
            goalieSeasonRepository.deleteAllInBatch();
            playerRepository.deleteAllInBatch();
            playerRepository.saveAll(players);
            skaterSeasonRepository.saveAll(skaterSeasons);
            goalieSeasonRepository.saveAll(goalieSeasons);
        });

        log.info("ESPN player sync complete for the {} pool: {} players ({} duplicates dropped), "
                        + "{} skater and {} goalie stat lines across seasons {}",
                poolSeason, players.size(), fetched.size() - pool.size(),
                skaterSeasons.size(), goalieSeasons.size(), playedSeasons);
        return new PlayerSyncResponse(players.size(), now);
    }

    /**
     * The seasons the payload has real numbers for. ESPN opens a season months before it is
     * played and reports all-zero totals for it in the meantime; storing those would say a
     * player had a scoreless season rather than no season yet.
     */
    private static Set<Integer> playedSeasons(List<FetchedPlayer> players) {
        Set<Integer> seasons = new TreeSet<>();
        for (FetchedPlayer player : players) {
            for (FetchedSkaterSeason line : player.skaterSeasons()) {
                if (line.gamesPlayed() != null && line.gamesPlayed() > 0) {
                    seasons.add(line.season());
                }
            }
            for (FetchedGoalieSeason line : player.goalieSeasons()) {
                if (line.gamesPlayed() != null && line.gamesPlayed() > 0) {
                    seasons.add(line.season());
                }
            }
        }
        return seasons;
    }

    /**
     * ESPN's player universe carries occasional duplicate records for the same person (same
     * name, position and jersey, different ids). Keep the one that actually played — a stale
     * duplicate would otherwise show up in the pool as a second, empty copy of the player.
     *
     * <p>The jersey is part of the key because two different people do share a name and a
     * position: the NHL has had two Matt Murrays in goal and two Connor Murphys on defence.
     * Collapsing those would throw away a real season.
     */
    private List<FetchedPlayer> dedupe(List<FetchedPlayer> players) {
        Map<String, FetchedPlayer> best = new LinkedHashMap<>();
        for (FetchedPlayer player : players) {
            String key = player.fullName().toLowerCase(Locale.ROOT) + "|" + player.position()
                    + "|" + player.sweaterNumber();
            FetchedPlayer existing = best.get(key);
            if (existing == null
                    || player.gamesPlayedIn(referenceSeason) > existing.gamesPlayedIn(referenceSeason)) {
                best.put(key, player);
            }
        }
        return new ArrayList<>(best.values());
    }

    private static EspnPlayer toEntity(FetchedPlayer player, Instant syncedAt) {
        EspnPlayer entity = new EspnPlayer();
        entity.id = player.id();
        entity.firstName = player.firstName();
        entity.lastName = player.lastName();
        entity.fullName = player.fullName();
        entity.position = player.position();
        entity.eligiblePositions = String.join(",", player.eligiblePositions());
        entity.sweaterNumber = player.sweaterNumber();
        entity.teamAbbrev = player.teamAbbrev();
        entity.headshot = player.headshot();
        entity.goalie = player.goalie();
        entity.active = player.active();
        entity.syncedAt = syncedAt;
        return entity;
    }

    private static EspnSkaterSeason toEntity(long playerId, FetchedSkaterSeason line, Instant syncedAt) {
        EspnSkaterSeason entity = new EspnSkaterSeason();
        entity.playerId = playerId;
        entity.season = line.season();
        entity.gamesPlayed = line.gamesPlayed();
        entity.goals = line.goals();
        entity.assists = line.assists();
        entity.points = line.points();
        entity.plusMinus = line.plusMinus();
        entity.pim = line.pim();
        entity.powerPlayGoals = line.powerPlayGoals();
        entity.powerPlayPoints = line.powerPlayPoints();
        entity.shorthandedGoals = line.shorthandedGoals();
        entity.shorthandedPoints = line.shorthandedPoints();
        entity.gameWinningGoals = line.gameWinningGoals();
        entity.shots = line.shots();
        entity.shootingPctg = line.shootingPctg();
        entity.avgToi = line.avgToi();
        entity.faceoffWinningPctg = line.faceoffWinningPctg();
        entity.hits = line.hits();
        entity.blockedShots = line.blockedShots();
        entity.totalFaceoffWins = line.totalFaceoffWins();
        entity.totalFaceoffLosses = line.totalFaceoffLosses();
        entity.hatTricks = line.hatTricks();
        entity.shifts = line.shifts();
        entity.timeOnIce = line.timeOnIce();
        entity.syncedAt = syncedAt;
        return entity;
    }

    private static EspnGoalieSeason toEntity(long playerId, FetchedGoalieSeason line, Instant syncedAt) {
        EspnGoalieSeason entity = new EspnGoalieSeason();
        entity.playerId = playerId;
        entity.season = line.season();
        entity.gamesPlayed = line.gamesPlayed();
        entity.gamesStarted = line.gamesStarted();
        entity.wins = line.wins();
        entity.losses = line.losses();
        entity.shutouts = line.shutouts();
        entity.shotsAgainst = line.shotsAgainst();
        entity.saves = line.saves();
        entity.goalsAgainst = line.goalsAgainst();
        entity.goalsAgainstAvg = line.goalsAgainstAvg();
        entity.savePctg = line.savePctg();
        entity.hatTricks = line.hatTricks();
        entity.overtimeLosses = line.overtimeLosses();
        entity.timeOnIce = line.timeOnIce();
        entity.syncedAt = syncedAt;
        return entity;
    }
}
