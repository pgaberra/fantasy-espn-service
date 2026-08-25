package com.fantasy.espn.player;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EspnSkaterSeasonRepository extends JpaRepository<EspnSkaterSeason, PlayerSeasonId> {

    List<EspnSkaterSeason> findAllBySeason(int season);
}
