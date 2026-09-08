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
 * <p>The startup run is started, not waited for. A sync is minutes of work — tens of megabytes
 * from ESPN plus a HEAD per player at the image CDN — and {@code ApplicationReadyEvent} is
 * delivered on the main thread, so waiting for it holds the boot sequence open for the whole
 * run. A container health check does not wait that long: promoting this service to production
 * on 2026-08-30 was rolled back as unhealthy while the first sync against an empty read model
 * was still going, and production sat on the previous version for over a week.
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
        try {
            if (!syncOnStartup || !isStale()) {
                return;
            }
            // startAsync, not sync: the run reports its own failures from the thread it runs
            // on, and startup must not wait for it. Returning false means a sync is already
            // under way, which is exactly what we wanted, so there is nothing to say about it.
            syncService.startAsync();
        } catch (Exception e) {
            // This listener runs inside the boot sequence: an exception here fails the whole
            // application context, so a database that is briefly unreachable would stop the
            // service from starting rather than cost it one refresh.
            log.error("ESPN player sync at startup could not be started", e);
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
