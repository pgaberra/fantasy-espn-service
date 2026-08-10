package com.fantasy.espn.player;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface EspnPlayerStatsRepository extends JpaRepository<EspnPlayerStats, Long> {

    /** When the cache was last written, or empty when nothing has been stored yet. */
    @Query("select max(stats.syncedAt) from EspnPlayerStats stats")
    Optional<Instant> findLastSyncedAt();
}
