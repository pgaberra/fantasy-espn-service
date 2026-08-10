package com.fantasy.espn.player;

import com.fantasy.espn.config.EspnProperties;
import com.fantasy.espn.player.dto.PlayerStatLine;
import com.fantasy.espn.player.dto.PlayerStatsResponse;
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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps a cached copy of ESPN's season stat lines and serves them to the BFF.
 *
 * <p>ESPN scores a handful of stats Yahoo does not report at all (hat tricks, shifts, goalie
 * overtime losses, time on ice), so a projection seeded from last season's Yahoo data has no
 * value to put in those columns. This read model is where those values come from.
 */
@Service
public class EspnPlayerStatsService {

    private static final Logger log = LoggerFactory.getLogger(EspnPlayerStatsService.class);

    private final EspnPlayerClient client;
    private final EspnPlayerStatsRepository repository;
    private final TransactionTemplate transactionTemplate;
    private final int season;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public EspnPlayerStatsService(EspnPlayerClient client,
                                  EspnPlayerStatsRepository repository,
                                  TransactionTemplate transactionTemplate,
                                  EspnProperties props) {
        this.client = client;
        this.repository = repository;
        this.transactionTemplate = transactionTemplate;
        this.season = props.playerStatsSeason();
    }

    public PlayerStatsResponse players() {
        List<EspnPlayerStats> rows = repository.findAll();
        Instant syncedAt = rows.stream().map(EspnPlayerStats::getSyncedAt)
                .max(Instant::compareTo).orElse(null);
        return new PlayerStatsResponse(rows.stream().map(EspnPlayerStatsService::toStatLine).toList(), syncedAt);
    }

    public PlayerSyncResponse sync() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A player stat sync is already running");
        }
        try {
            return runSync();
        } finally {
            running.set(false);
        }
    }

    private PlayerSyncResponse runSync() {
        List<PlayerStatLine> fetched = client.fetchSeasonStats(season);
        if (fetched.isEmpty()) {
            // Never wipe the cache on an empty fetch — ESPN is the only source for these stats.
            throw new IllegalStateException("ESPN returned no player stat lines; preserving existing data");
        }
        List<PlayerStatLine> deduped = dedupe(fetched);

        Instant now = Instant.now();
        List<EspnPlayerStats> rows = deduped.stream().map(line -> toEntity(line, now)).toList();
        // Replace in one transaction so a failure mid-write can't leave the cache empty.
        transactionTemplate.executeWithoutResult(status -> {
            repository.deleteAllInBatch();
            repository.saveAll(rows);
        });

        log.info("ESPN player stat sync complete for season {}: {} stat lines ({} duplicates dropped)",
                season, rows.size(), fetched.size() - deduped.size());
        return new PlayerSyncResponse(rows.size(), now);
    }

    /**
     * ESPN's player universe carries occasional duplicate records for the same person (same
     * name and position, different ids). Keep the one that actually played — a stale duplicate
     * would otherwise make the BFF's name match ambiguous and drop the player's stats.
     */
    private static List<PlayerStatLine> dedupe(List<PlayerStatLine> lines) {
        Map<String, PlayerStatLine> best = new LinkedHashMap<>();
        for (PlayerStatLine line : lines) {
            String key = line.fullName().toLowerCase(Locale.ROOT) + "|" + line.position();
            PlayerStatLine existing = best.get(key);
            if (existing == null || gamesPlayed(line) > gamesPlayed(existing)) {
                best.put(key, line);
            }
        }
        return new ArrayList<>(best.values());
    }

    private static int gamesPlayed(PlayerStatLine line) {
        return line.gamesPlayed() == null ? 0 : line.gamesPlayed();
    }

    private static EspnPlayerStats toEntity(PlayerStatLine line, Instant syncedAt) {
        EspnPlayerStats entity = new EspnPlayerStats();
        entity.setId(line.id());
        entity.setFullName(line.fullName());
        entity.setPosition(line.position());
        entity.setGamesPlayed(line.gamesPlayed());
        entity.setHatTricks(line.hatTricks());
        entity.setShifts(line.shifts());
        entity.setOvertimeLosses(line.overtimeLosses());
        entity.setTimeOnIce(line.timeOnIce());
        entity.setSyncedAt(syncedAt);
        return entity;
    }

    private static PlayerStatLine toStatLine(EspnPlayerStats entity) {
        Long id = entity.getId();
        return new PlayerStatLine(
                id == null ? 0L : id,
                entity.getFullName(),
                entity.getPosition(),
                entity.getGamesPlayed(),
                entity.getHatTricks(),
                entity.getShifts(),
                entity.getOvertimeLosses(),
                entity.getTimeOnIce());
    }
}
