package com.fantasy.espn.player;

import com.fantasy.espn.player.dto.PlayerSyncResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Refreshes the cached ESPN stat lines daily, and once at startup when the cache is empty so
 * a fresh deployment serves the stats without waiting for the next scheduled run.
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
        if (syncOnStartup && repository.count() == 0) {
            runSync("startup");
        }
    }

    @Scheduled(cron = "${espn.player-sync-cron:0 45 7 * * *}", zone = "UTC")
    public void syncDaily() {
        runSync("scheduled");
    }

    private void runSync(String trigger) {
        try {
            PlayerSyncResponse result = playerStatsService.sync();
            log.info("ESPN player stat sync ({}) stored {} stat lines", trigger, result.players());
        } catch (Exception e) {
            log.error("ESPN player stat sync ({}) failed", trigger, e);
        }
    }
}
