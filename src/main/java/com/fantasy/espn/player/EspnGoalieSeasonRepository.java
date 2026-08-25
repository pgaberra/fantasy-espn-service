package com.fantasy.espn.player;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EspnGoalieSeasonRepository extends JpaRepository<EspnGoalieSeason, PlayerSeasonId> {

    List<EspnGoalieSeason> findAllBySeason(int season);
}
