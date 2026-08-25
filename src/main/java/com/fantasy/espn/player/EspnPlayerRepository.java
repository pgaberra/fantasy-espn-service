package com.fantasy.espn.player;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EspnPlayerRepository extends JpaRepository<EspnPlayer, Long> {

    /** When the pool was last written, or empty when nothing has been stored yet. */
    @Query("select max(player.syncedAt) from EspnPlayer player")
    Optional<Instant> findLastSyncedAt();

    List<EspnPlayer> findAllByGoalieAndActiveIsTrueOrderByLastNameAscFirstNameAsc(boolean goalie);
}
