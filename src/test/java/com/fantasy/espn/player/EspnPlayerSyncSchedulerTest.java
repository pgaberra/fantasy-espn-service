package com.fantasy.espn.player;

import com.fantasy.espn.player.dto.PlayerSyncResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EspnPlayerSyncSchedulerTest {

    private EspnPlayerStatsService playerStatsService;
    private EspnPlayerStatsRepository repository;

    @BeforeEach
    void setUp() {
        playerStatsService = mock(EspnPlayerStatsService.class);
        repository = mock(EspnPlayerStatsRepository.class);
        when(playerStatsService.sync()).thenReturn(new PlayerSyncResponse(1700, Instant.now()));
    }

    private EspnPlayerSyncScheduler scheduler(boolean syncOnStartup) {
        return new EspnPlayerSyncScheduler(playerStatsService, repository, syncOnStartup, Duration.ofHours(36));
    }

    @Test
    void startup_syncsWhenNothingIsCached() {
        when(repository.findLastSyncedAt()).thenReturn(Optional.empty());

        scheduler(true).syncOnStartupWhenStale();

        verify(playerStatsService).sync();
    }

    @Test
    void startup_syncsWhenTheCacheIsOlderThanTheLimit() {
        // A deployment that changes what the sync stores leaves a full but outdated cache.
        when(repository.findLastSyncedAt())
                .thenReturn(Optional.of(Instant.now().minus(Duration.ofHours(48))));

        scheduler(true).syncOnStartupWhenStale();

        verify(playerStatsService).sync();
    }

    @Test
    void startup_leavesAFreshCacheAlone() {
        // An ordinary restart must not re-fetch 33 MB for nothing.
        when(repository.findLastSyncedAt())
                .thenReturn(Optional.of(Instant.now().minus(Duration.ofHours(2))));

        scheduler(true).syncOnStartupWhenStale();

        verify(playerStatsService, never()).sync();
    }

    @Test
    void startup_doesNothingWhenTurnedOff() {
        scheduler(false).syncOnStartupWhenStale();

        verify(playerStatsService, never()).sync();
        verify(repository, never()).findLastSyncedAt();
    }

    @Test
    void aFailedSyncNeverEscapesTheScheduler() {
        // ESPN being unreachable must not take the startup event or the cron thread down.
        when(repository.findLastSyncedAt()).thenReturn(Optional.empty());
        when(playerStatsService.sync()).thenThrow(new IllegalStateException("ESPN unreachable"));
        EspnPlayerSyncScheduler scheduler = scheduler(true);

        assertThatNoException().isThrownBy(scheduler::syncOnStartupWhenStale);
        assertThatNoException().isThrownBy(scheduler::syncDaily);
    }
}
