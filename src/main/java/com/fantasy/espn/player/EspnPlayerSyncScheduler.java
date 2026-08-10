package com.fantasy.espn.player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Refreshes the cached ESPN stat lines nightly, and once at startup when the cache is empty so
 * a fresh deployment serves the stats without waiting for the next scheduled run.
 *
 * <p>A successful run is logged by the service itself; only failures are reported here, and a
 * failure is never fatal — the previous stat lines stay in place until the next attempt.
 */
@Component
public class EspnPlayerSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(EspnPlayerSyncScheduler.class);

    private final EspnPlayerStatsService playerStatsService;
    private final EspnPlayerStatsRepository repository;
    private final boolean syncOnStartup;

    public EspnPlayerSyncScheduler(EspnPlayerStatsService playerStatsService,
                                   EspnPlayerStatsRepository repository,
                                   @Value("${espn.player-sync-on-startup:true}") boolean syncOnStartup) {
        this.playerStatsService = playerStatsService;
        this.repository = repository;
        this.syncOnStartup = syncOnStartup;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartupWhenEmpty() {
        if (!syncOnStartup || repository.count() > 0) {
            return;
        }
        try {
            playerStatsService.sync();
        } catch (Exception e) {
            log.error("ESPN player stat sync at startup failed", e);
        }
    }

    @Scheduled(cron = "${espn.player-sync-cron:0 45 7 * * *}", zone = "UTC")
    public void syncDaily() {
        try {
            playerStatsService.sync();
        } catch (Exception e) {
            log.error("Nightly ESPN player stat sync failed", e);
        }
    }
}
