package com.fantasy.espn.player;

import com.fantasy.espn.config.EspnProperties;
import com.fantasy.espn.player.dto.PlayerSyncResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EspnPlayerSyncServiceTest {

    private static final int POOL_SEASON = 2026;
    private static final int REFERENCE_SEASON = 2025;

    private EspnPlayerClient client;
    private EspnPlayerRepository playerRepository;
    private EspnSkaterSeasonRepository skaterSeasonRepository;
    private EspnGoalieSeasonRepository goalieSeasonRepository;
    private EspnPlayerSyncService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        client = mock(EspnPlayerClient.class);
        playerRepository = mock(EspnPlayerRepository.class);
        skaterSeasonRepository = mock(EspnSkaterSeasonRepository.class);
        goalieSeasonRepository = mock(EspnGoalieSeasonRepository.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, Consumer.class).accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        EspnHeadshotVerifier headshotVerifier = mock(EspnHeadshotVerifier.class);
        when(headshotVerifier.withVerifiedHeadshots(any()))
                .thenAnswer(call -> call.getArgument(0));
        service = new EspnPlayerSyncService(client, headshotVerifier, playerRepository, skaterSeasonRepository,
                goalieSeasonRepository, transactionTemplate,
                new EspnProperties("https://espn.test", "fhl", 2027, POOL_SEASON, REFERENCE_SEASON, null));
    }

    private static FetchedSkaterSeason skaterSeason(int season, Integer gamesPlayed) {
        return new FetchedSkaterSeason(season, gamesPlayed, 48, 90, 138, 17, 44, 13, 54, 1, 2, 4,
                300, 0.16, "22:59", 0.495, 40, 30, 457, 466, 3, 1841, 113127);
    }

    private static FetchedGoalieSeason goalieSeason(int season, Integer gamesPlayed) {
        return new FetchedGoalieSeason(season, gamesPlayed, 58, 39, 15, 2, 1483, 1353, 132,
                2.31, 0.912, 0, 4, 205845);
    }

    private static FetchedPlayer skater(long id, String fullName, Integer jersey,
                                        List<FetchedSkaterSeason> seasons) {
        return skater(id, fullName, jersey, true, seasons);
    }

    private static FetchedPlayer skater(long id, String fullName, Integer jersey, boolean active,
                                        List<FetchedSkaterSeason> seasons) {
        return new FetchedPlayer(id, "First", fullName, fullName, "C", List.of("C", "LW"), jersey,
                "EDM", "https://espn.test/" + id + ".png", active, false, seasons, List.of());
    }

    private static FetchedPlayer goalie(long id, String fullName, Integer jersey,
                                        List<FetchedGoalieSeason> seasons) {
        return new FetchedPlayer(id, "First", fullName, fullName, "G", List.of("G"), jersey,
                "TB", "https://espn.test/" + id + ".png", true, true, List.of(), seasons);
    }

    /** A season's worth of players, so the "this looks truncated" guard doesn't fire. */
    private static List<FetchedPlayer> aSeasonOf(List<FetchedPlayer> players, Integer fillerGamesPlayed) {
        List<FetchedPlayer> all = new ArrayList<>(players);
        for (int i = 0; i < 250; i++) {
            all.add(skater(9000L + i, "Filler " + i, i,
                    List.of(skaterSeason(REFERENCE_SEASON, fillerGamesPlayed))));
        }
        return all;
    }

    private List<EspnPlayer> savedPlayers() {
        ArgumentCaptor<List<EspnPlayer>> captor = ArgumentCaptor.captor();
        verify(playerRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private List<EspnSkaterSeason> savedSkaterSeasons() {
        ArgumentCaptor<List<EspnSkaterSeason>> captor = ArgumentCaptor.captor();
        verify(skaterSeasonRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private void nothingWasWritten() {
        verify(playerRepository, never()).deleteAllInBatch();
        verify(playerRepository, never()).saveAll(any());
        verify(skaterSeasonRepository, never()).saveAll(any());
        verify(goalieSeasonRepository, never()).saveAll(any());
    }

    @Test
    void sync_storesIdentityEligibilityAndTheStatLine() {
        when(client.fetchPlayers(POOL_SEASON)).thenReturn(aSeasonOf(List.of(
                skater(1L, "Connor McDavid", 97, List.of(skaterSeason(REFERENCE_SEASON, 82)))), 10));

        PlayerSyncResponse result = service.sync().orElseThrow();

        assertThat(result.players()).isEqualTo(251);
        assertThat(savedPlayers())
                .filteredOn(row -> row.fullName.equals("Connor McDavid"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.position).isEqualTo("C");
                    assertThat(row.eligiblePositions).isEqualTo("C,LW");
                    assertThat(row.sweaterNumber).isEqualTo(97);
                    assertThat(row.teamAbbrev).isEqualTo("EDM");
                    assertThat(row.headshot).isEqualTo("https://espn.test/1.png");
                    assertThat(row.goalie).isFalse();
                    assertThat(row.active).isTrue();
                    assertThat(row.syncedAt).isNotNull();
                });
        assertThat(savedSkaterSeasons())
                .filteredOn(row -> row.playerId == 1L)
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.season).isEqualTo(REFERENCE_SEASON);
                    assertThat(row.points).isEqualTo(138);
                    assertThat(row.hatTricks).isEqualTo(3);
                    assertThat(row.avgToi).isEqualTo("22:59");
                });
    }

    @Test
    void sync_storesGoalieLinesInTheirOwnTable() {
        when(client.fetchPlayers(POOL_SEASON)).thenReturn(aSeasonOf(List.of(
                goalie(1L, "Andrei Vasilevskiy", 88,
                        List.of(goalieSeason(REFERENCE_SEASON, 58)))), 10));

        service.sync();

        ArgumentCaptor<List<EspnGoalieSeason>> captor = ArgumentCaptor.captor();
        verify(goalieSeasonRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(row -> {
            assertThat(row.playerId).isEqualTo(1L);
            assertThat(row.wins).isEqualTo(39);
            assertThat(row.overtimeLosses).isEqualTo(4);
        });
    }

    /**
     * ESPN opens a season months before it is played and reports all-zero totals for it. Storing
     * those would say every player had a scoreless season rather than no season yet.
     */
    @Test
    void sync_storesOnlyTheSeasonsThatHaveActuallyBeenPlayed() {
        when(client.fetchPlayers(POOL_SEASON)).thenReturn(aSeasonOf(List.of(
                skater(1L, "Connor McDavid", 97, List.of(
                        skaterSeason(REFERENCE_SEASON, 82),
                        skaterSeason(POOL_SEASON, 0)))), 10));

        service.sync();

        assertThat(savedSkaterSeasons())
                .filteredOn(row -> row.playerId == 1L)
                .extracting(row -> row.season)
                .containsExactly(REFERENCE_SEASON);
    }

    @Test
    void sync_keepsTheDuplicateThatActuallyPlayed() {
        // Same person, listed twice by ESPN: same name, position and jersey, different ids.
        when(client.fetchPlayers(POOL_SEASON)).thenReturn(aSeasonOf(List.of(
                skater(1L, "Matt Murray", 30, List.of(skaterSeason(REFERENCE_SEASON, 0))),
                skater(2L, "Matt Murray", 30, List.of(skaterSeason(REFERENCE_SEASON, 5)))), 10));

        service.sync();

        assertThat(savedPlayers())
                .filteredOn(row -> row.fullName.equals("Matt Murray"))
                .singleElement()
                .satisfies(row -> assertThat(row.id).isEqualTo(2L));
    }

    @Test
    void sync_keepsTwoPlayersWhoShareANameAndPosition() {
        // Two different people: the NHL has had two Matt Murrays in goal at once. Collapsing
        // them would throw away a real season.
        when(client.fetchPlayers(POOL_SEASON)).thenReturn(aSeasonOf(List.of(
                skater(1L, "Matt Murray", 30, List.of(skaterSeason(REFERENCE_SEASON, 5))),
                skater(2L, "Matt Murray", 32, List.of(skaterSeason(REFERENCE_SEASON, 0)))), 10));

        service.sync();

        assertThat(savedPlayers())
                .filteredOn(row -> row.fullName.equals("Matt Murray"))
                .hasSize(2)
                .extracting(row -> row.sweaterNumber)
                .containsExactlyInAnyOrder(30, 32);
    }

    /**
     * Someone who retired after the last season played is out of the pool, but a projection
     * still references their numbers, so the row stays.
     */
    @Test
    void sync_keepsARetiredPlayerWhoStillHasAStatLine() {
        when(client.fetchPlayers(POOL_SEASON)).thenReturn(aSeasonOf(List.of(
                skater(1L, "Just Retired", 12, false, List.of(skaterSeason(REFERENCE_SEASON, 71))),
                skater(2L, "Never Played", 13, false, List.of())), 10));

        service.sync();

        assertThat(savedPlayers()).extracting(row -> row.fullName)
                .contains("Just Retired")
                .doesNotContain("Never Played");
    }

    @Test
    void sync_preservesExistingDataWhenEspnReturnsNothing() {
        when(client.fetchPlayers(POOL_SEASON)).thenReturn(List.of());

        assertThatThrownBy(() -> service.sync()).isInstanceOf(IllegalStateException.class);

        nothingWasWritten();
    }

    @Test
    void sync_preservesExistingDataWhenOnlyOnePageCameBack() {
        // ESPN pages the player endpoint at 50 and says nothing about the rest, so a dropped
        // request header arrives as a short but otherwise valid-looking list.
        List<FetchedPlayer> onePage = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            onePage.add(skater(i, "Player " + i, i, List.of(skaterSeason(REFERENCE_SEASON, 60))));
        }
        when(client.fetchPlayers(POOL_SEASON)).thenReturn(onePage);

        assertThatThrownBy(() -> service.sync())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("far fewer than a season holds");

        nothingWasWritten();
    }

    @Test
    void sync_preservesExistingDataWhenTheReferenceSeasonHasNotBeenPlayedYet() {
        // ESPN answers for an upcoming season with a full set of all-zero rows, not with
        // nothing, so an off-by-one season would replace real stats with zeroes.
        when(client.fetchPlayers(POOL_SEASON)).thenReturn(aSeasonOf(List.of(
                skater(1L, "Connor McDavid", 97, List.of(skaterSeason(REFERENCE_SEASON, 0)))), 0));

        assertThatThrownBy(() -> service.sync())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no games played");

        nothingWasWritten();
    }

    @Test
    void sync_replacesTheWholeReadModelInOneTransaction() {
        when(client.fetchPlayers(POOL_SEASON)).thenReturn(aSeasonOf(List.of(), 10));

        service.sync();

        verify(skaterSeasonRepository).deleteAllInBatch();
        verify(goalieSeasonRepository).deleteAllInBatch();
        verify(playerRepository).deleteAllInBatch();
    }
}
