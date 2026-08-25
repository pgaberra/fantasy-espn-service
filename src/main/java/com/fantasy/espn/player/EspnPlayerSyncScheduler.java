package com.fantasy.espn.player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Refreshes the cached ESPN player read model nightly, and once at startup when what's cached is
 * missing or old.
 *
 * <p>Staleness is what's checked, not emptiness. A deployment that changes what the sync
 * stores — a new column, a corrected parse — leaves a full but outdated cache behind, and an
 * empty-only check would sit on it until the next nightly run with nothing to indicate the
 * data didn't match the code. That happened twice while this service was being built.
 *
 * <p>A successful run is logged by the service itself; only failures are reported here, and a
 * failure is never fatal — the previous read model stays in place until the next attempt.
 */
@Component
public class EspnPlayerSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(EspnPlayerSyncScheduler.class);

    private final EspnPlayerSyncService syncService;
    private final EspnPlayerRepository repository;
    private final boolean syncOnStartup;
    private final Duration maxAge;

    public EspnPlayerSyncScheduler(EspnPlayerSyncService syncService,
                                   EspnPlayerRepository repository,
                                   @Value("${espn.player-sync-on-startup:true}") boolean syncOnStartup,
                                   @Value("${espn.player-stats-max-age:36h}") Duration maxAge) {
        this.syncService = syncService;
        this.repository = repository;
        this.syncOnStartup = syncOnStartup;
        this.maxAge = maxAge;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartupWhenStale() {
        if (!syncOnStartup || !isStale()) {
            return;
        }
        try {
            syncService.sync();
        } catch (Exception e) {
            log.error("ESPN player sync at startup failed", e);
        }
    }

    @Scheduled(cron = "${espn.player-sync-cron:0 45 7 * * *}", zone = "UTC")
    public void syncDaily() {
        try {
            syncService.sync();
        } catch (Exception e) {
            log.error("Nightly ESPN player sync failed", e);
        }
    }

    private boolean isStale() {
        Optional<Instant> lastSyncedAt = repository.findLastSyncedAt();
        if (lastSyncedAt.isEmpty()) {
            return true;
        }
        return lastSyncedAt.get().isBefore(Instant.now().minus(maxAge));
    }
}
