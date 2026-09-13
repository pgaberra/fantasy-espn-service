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

    private EspnPlayerSyncService syncService;
    private EspnPlayerRepository repository;

    @BeforeEach
    void setUp() {
        syncService = mock(EspnPlayerSyncService.class);
        repository = mock(EspnPlayerRepository.class);
        when(syncService.sync()).thenReturn(Optional.of(new PlayerSyncResponse(1700, Instant.now())));
        when(syncService.startAsync()).thenReturn(true);
    }

    private EspnPlayerSyncScheduler scheduler(boolean syncOnStartup) {
        return new EspnPlayerSyncScheduler(syncService, repository, syncOnStartup, Duration.ofHours(36));
    }

    @Test
    void startup_syncsWhenNothingIsCached() {
        when(repository.findLastSyncedAt()).thenReturn(Optional.empty());

        scheduler(true).syncOnStartupWhenStale();

        verify(syncService).startAsync();
    }

    @Test
    void startup_syncsWhenTheCacheIsOlderThanTheLimit() {
        // A deployment that changes what the sync stores leaves a full but outdated cache.
        when(repository.findLastSyncedAt())
                .thenReturn(Optional.of(Instant.now().minus(Duration.ofHours(48))));

        scheduler(true).syncOnStartupWhenStale();

        verify(syncService).startAsync();
    }

    @Test
    void startup_neverWaitsForTheSync() {
        // ApplicationReadyEvent is delivered on the main thread, and a sync is minutes of work.
        // Waiting for it once held startup open long enough for the container health check to
        // call the service unhealthy and roll the production deployment back.
        when(repository.findLastSyncedAt()).thenReturn(Optional.empty());

        scheduler(true).syncOnStartupWhenStale();

        verify(syncService, never()).sync();
    }

    @Test
    void startup_leavesAFreshCacheAlone() {
        // An ordinary restart must not re-fetch 33 MB for nothing.
        when(repository.findLastSyncedAt())
                .thenReturn(Optional.of(Instant.now().minus(Duration.ofHours(2))));

        scheduler(true).syncOnStartupWhenStale();

        verify(syncService, never()).startAsync();
    }

    @Test
    void startup_doesNothingWhenTurnedOff() {
        scheduler(false).syncOnStartupWhenStale();

        verify(syncService, never()).startAsync();
        verify(repository, never()).findLastSyncedAt();
    }

    @Test
    void nightly_overlappingATriggeredSync_isAQuietSkip() {
        // The run under way is doing the same work, so this is not a failed nightly run.
        when(syncService.sync()).thenReturn(Optional.empty());

        assertThatNoException().isThrownBy(scheduler(true)::syncDaily);
        verify(syncService).sync();
    }

    @Test
    void aFailedSyncNeverEscapesTheScheduler() {
        // ESPN being unreachable must not take the startup event or the cron thread down.
        when(repository.findLastSyncedAt()).thenReturn(Optional.empty());
        when(syncService.sync()).thenThrow(new IllegalStateException("ESPN unreachable"));
        when(syncService.startAsync()).thenThrow(new IllegalStateException("ESPN unreachable"));
        EspnPlayerSyncScheduler scheduler = scheduler(true);

        assertThatNoException().isThrownBy(scheduler::syncOnStartupWhenStale);
        assertThatNoException().isThrownBy(scheduler::syncDaily);
    }
}
