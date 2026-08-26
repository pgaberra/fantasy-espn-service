package com.fantasy.espn.player;

import com.fantasy.espn.config.EspnProperties;
import com.fantasy.espn.player.dto.GoalieResponse;
import com.fantasy.espn.player.dto.PlayerStatsResponse;
import com.fantasy.espn.player.dto.SkaterResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EspnPlayerServiceTest {

    private static final int REFERENCE_SEASON = 2025;
    private static final Instant SYNCED_AT = Instant.parse("2026-08-25T07:45:00Z");

    private EspnPlayerRepository playerRepository;
    private EspnSkaterSeasonRepository skaterSeasonRepository;
    private EspnGoalieSeasonRepository goalieSeasonRepository;
    private EspnPlayerSyncService syncService;
    private EspnPlayerService service;

    @BeforeEach
    void setUp() {
        playerRepository = mock(EspnPlayerRepository.class);
        skaterSeasonRepository = mock(EspnSkaterSeasonRepository.class);
        goalieSeasonRepository = mock(EspnGoalieSeasonRepository.class);
        syncService = mock(EspnPlayerSyncService.class);
        service = new EspnPlayerService(playerRepository, skaterSeasonRepository,
                goalieSeasonRepository, syncService,
                new EspnProperties("https://espn.test", "fhl", 2027, 2026, REFERENCE_SEASON, null));
    }

    private static EspnPlayer player(long id, String fullName, String position,
                                     String eligiblePositions, boolean goalie) {
        EspnPlayer player = new EspnPlayer();
        player.id = id;
        player.firstName = fullName.split(" ")[0];
        player.lastName = fullName.substring(fullName.indexOf(' ') + 1);
        player.fullName = fullName;
        player.position = position;
        player.eligiblePositions = eligiblePositions;
        player.sweaterNumber = 97;
        player.teamAbbrev = "EDM";
        player.headshot = "https://espn.test/" + id + ".png";
        player.goalie = goalie;
        player.active = true;
        player.syncedAt = SYNCED_AT;
        return player;
    }

    private static EspnSkaterSeason skaterSeason(long playerId, int season, int points) {
        EspnSkaterSeason line = new EspnSkaterSeason();
        line.playerId = playerId;
        line.season = season;
        line.gamesPlayed = 82;
        line.points = points;
        line.shifts = 1841;
        line.hatTricks = 3;
        line.timeOnIce = 113127;
        line.syncedAt = SYNCED_AT;
        return line;
    }

    private static EspnGoalieSeason goalieSeason(long playerId, int season, int wins) {
        EspnGoalieSeason line = new EspnGoalieSeason();
        line.playerId = playerId;
        line.season = season;
        line.gamesPlayed = 58;
        line.wins = wins;
        line.overtimeLosses = 4;
        line.timeOnIce = 205845;
        line.syncedAt = SYNCED_AT;
        return line;
    }

    private void poolIs(List<EspnPlayer> players) {
        when(playerRepository.findAllByGoalieAndActiveIsTrueOrderByLastNameAscFirstNameAsc(false))
                .thenReturn(players.stream().filter(p -> !p.goalie).toList());
        when(playerRepository.findAllByGoalieAndActiveIsTrueOrderByLastNameAscFirstNameAsc(true))
                .thenReturn(players.stream().filter(p -> p.goalie).toList());
        when(playerRepository.findAll()).thenReturn(players);
    }

    @Test
    void getSkaters_joinsTheAskedForSeasonOntoTheIdentity() {
        poolIs(List.of(player(1L, "Connor McDavid", "C", "C,LW", false)));
        when(skaterSeasonRepository.findAllBySeason(REFERENCE_SEASON))
                .thenReturn(List.of(skaterSeason(1L, REFERENCE_SEASON, 138)));

        SkaterResponse skater = service.getSkaters(REFERENCE_SEASON).getFirst();

        assertThat(skater.id()).isEqualTo(1L);
        assertThat(skater.firstName()).isEqualTo("Connor");
        assertThat(skater.lastName()).isEqualTo("McDavid");
        assertThat(skater.position()).isEqualTo("C");
        assertThat(skater.eligiblePositions()).containsExactly("C", "LW");
        assertThat(skater.teamAbbrev()).isEqualTo("EDM");
        assertThat(skater.headshot()).isEqualTo("https://espn.test/1.png");
        assertThat(skater.points()).isEqualTo(138);
        assertThat(skater.shifts()).isEqualTo(1841);
    }

    /** A rookie, a call-up, or anyone who did not play that year is in the pool all the same. */
    @Test
    void getSkaters_returnsThePlayerWithoutNumbersWhenThatSeasonHasNoLine() {
        poolIs(List.of(player(1L, "Connor McDavid", "C", "C,LW", false)));
        when(skaterSeasonRepository.findAllBySeason(2024)).thenReturn(List.of());

        SkaterResponse skater = service.getSkaters(2024).getFirst();

        assertThat(skater.lastName()).isEqualTo("McDavid");
        assertThat(skater.gamesPlayed()).isNull();
        assertThat(skater.points()).isNull();
    }

    @Test
    void getGoalies_joinsTheAskedForSeasonOntoTheIdentity() {
        poolIs(List.of(player(2L, "Andrei Vasilevskiy", "G", "G", true)));
        when(goalieSeasonRepository.findAllBySeason(REFERENCE_SEASON))
                .thenReturn(List.of(goalieSeason(2L, REFERENCE_SEASON, 39)));

        GoalieResponse goalie = service.getGoalies(REFERENCE_SEASON).getFirst();

        assertThat(goalie.position()).isEqualTo("G");
        assertThat(goalie.eligiblePositions()).containsExactly("G");
        assertThat(goalie.wins()).isEqualTo(39);
        assertThat(goalie.overtimeLosses()).isEqualTo(4);
    }

    @Test
    void lastSync_reportsWhenThePoolWasWrittenAndHowBigItIs() {
        when(playerRepository.findLastSyncedAt()).thenReturn(Optional.of(SYNCED_AT));
        when(playerRepository.count()).thenReturn(1686L);

        assertThat(service.lastSync().syncedAt()).isEqualTo(SYNCED_AT);
        assertThat(service.lastSync().players()).isEqualTo(1686L);
    }

    /** Nothing synced yet is a fact worth reporting, not an error. */
    /** A caller polls this to find out when a triggered sync is done. */
    @Test
    void lastSync_saysWhetherOneIsRunningRightNow() {
        when(playerRepository.findLastSyncedAt()).thenReturn(Optional.of(SYNCED_AT));
        when(playerRepository.count()).thenReturn(1659L);
        when(syncService.isRunning()).thenReturn(true);

        assertThat(service.lastSync().running()).isTrue();
    }

    @Test
    void lastSync_reportsNoTimeAtAllBeforeTheFirstSync() {
        when(playerRepository.findLastSyncedAt()).thenReturn(Optional.empty());
        when(playerRepository.count()).thenReturn(0L);

        assertThat(service.lastSync().syncedAt()).isNull();
    }

    @Test
    void players_servesTheReferenceSeasonWithTheStatsYahooDoesNotReport() {
        poolIs(List.of(
                player(1L, "Connor McDavid", "C", "C,LW", false),
                player(2L, "Andrei Vasilevskiy", "G", "G", true),
                player(3L, "Never Played", "D", "D", false)));
        when(skaterSeasonRepository.findAllBySeason(REFERENCE_SEASON))
                .thenReturn(List.of(skaterSeason(1L, REFERENCE_SEASON, 138)));
        when(goalieSeasonRepository.findAllBySeason(REFERENCE_SEASON))
                .thenReturn(List.of(goalieSeason(2L, REFERENCE_SEASON, 39)));

        PlayerStatsResponse response = service.players();

        assertThat(response.syncedAt()).isEqualTo(SYNCED_AT);
        // Only players with a line to contribute: the BFF matches these on by name.
        assertThat(response.players()).hasSize(2);
        assertThat(response.players().getFirst()).satisfies(line -> {
            assertThat(line.fullName()).isEqualTo("Connor McDavid");
            assertThat(line.shifts()).isEqualTo(1841);
            assertThat(line.hatTricks()).isEqualTo(3);
            assertThat(line.overtimeLosses()).isNull();
        });
        assertThat(response.players().get(1)).satisfies(line -> {
            assertThat(line.fullName()).isEqualTo("Andrei Vasilevskiy");
            assertThat(line.overtimeLosses()).isEqualTo(4);
            assertThat(line.shifts()).isNull();
        });
    }
}
