package com.fantasy.espn.player;

import com.fantasy.espn.config.EspnProperties;
import com.fantasy.espn.player.dto.PlayerStatLine;
import com.fantasy.espn.player.dto.PlayerSyncResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
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

class EspnPlayerStatsServiceTest {

    private EspnPlayerClient client;
    private EspnPlayerStatsRepository repository;
    private EspnPlayerStatsService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        client = mock(EspnPlayerClient.class);
        repository = mock(EspnPlayerStatsRepository.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, Consumer.class).accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        service = new EspnPlayerStatsService(client, repository, transactionTemplate,
                new EspnProperties("https://espn.test", "fhl", 2027, 2026, null));
    }

    /** A season's worth of players, so the "this looks truncated" guard doesn't fire. */
    private static List<PlayerStatLine> aSeasonOf(List<PlayerStatLine> lines, int fillerGamesPlayed) {
        List<PlayerStatLine> all = new ArrayList<>(lines);
        for (int i = 0; i < 250; i++) {
            all.add(new PlayerStatLine(9000L + i, "Filler " + i, "C", i, fillerGamesPlayed, 0, 0, null, 0));
        }
        return all;
    }

    private static List<EspnPlayerStats> saved(EspnPlayerStatsRepository repository) {
        ArgumentCaptor<List<EspnPlayerStats>> captor = ArgumentCaptor.captor();
        verify(repository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void sync_storesFetchedStatLines() {
        when(client.fetchSeasonStats(2026)).thenReturn(aSeasonOf(List.of(
                new PlayerStatLine(1L, "Connor McDavid", "C", null, 67, 2, 1413, null, 88566)), 10));

        PlayerSyncResponse result = service.sync();

        assertThat(result.players()).isEqualTo(251);
        assertThat(saved(repository))
                .filteredOn(row -> row.getFullName().equals("Connor McDavid"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getHatTricks()).isEqualTo(2);
                    assertThat(row.getShifts()).isEqualTo(1413);
                    assertThat(row.getSyncedAt()).isNotNull();
                });
    }

    @Test
    void sync_keepsTheDuplicateThatActuallyPlayed() {
        // Same person, listed twice by ESPN: same name, position and jersey, different ids.
        when(client.fetchSeasonStats(2026)).thenReturn(aSeasonOf(List.of(
                new PlayerStatLine(1L, "Matt Murray", "G", 30, 0, null, null, 0, 0),
                new PlayerStatLine(2L, "Matt Murray", "G", 30, 5, null, null, 1, 18000)), 10));

        service.sync();

        assertThat(saved(repository))
                .filteredOn(row -> row.getFullName().equals("Matt Murray"))
                .singleElement()
                .satisfies(row -> assertThat(row.getId()).isEqualTo(2L));
    }

    @Test
    void sync_keepsTwoPlayersWhoShareANameAndPosition() {
        // Two different people: the NHL has had two Matt Murrays in goal at once. Collapsing
        // them would throw away a real season and leave the BFF unable to tell them apart.
        when(client.fetchSeasonStats(2026)).thenReturn(aSeasonOf(List.of(
                new PlayerStatLine(1L, "Matt Murray", "G", 30, 5, null, null, 1, 18000),
                new PlayerStatLine(2L, "Matt Murray", "G", 32, 0, null, null, 0, 0)), 10));

        service.sync();

        assertThat(saved(repository))
                .filteredOn(row -> row.getFullName().equals("Matt Murray"))
                .hasSize(2)
                .extracting(EspnPlayerStats::getSweaterNumber)
                .containsExactlyInAnyOrder(30, 32);
    }

    @Test
    void sync_preservesExistingDataWhenEspnReturnsNothing() {
        when(client.fetchSeasonStats(2026)).thenReturn(List.of());

        assertThatThrownBy(() -> service.sync()).isInstanceOf(IllegalStateException.class);

        verify(repository, never()).deleteAllInBatch();
        verify(repository, never()).saveAll(any());
    }

    @Test
    void sync_preservesExistingDataWhenOnlyOnePageCameBack() {
        // ESPN pages the player endpoint at 50 and says nothing about the rest, so a dropped
        // request header arrives as a short but otherwise valid-looking list.
        List<PlayerStatLine> onePage = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            onePage.add(new PlayerStatLine(i, "Player " + i, "C", i, 60, 1, 1200, null, 70000));
        }
        when(client.fetchSeasonStats(2026)).thenReturn(onePage);

        assertThatThrownBy(() -> service.sync())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("far fewer than a season holds");

        verify(repository, never()).deleteAllInBatch();
        verify(repository, never()).saveAll(any());
    }

    @Test
    void sync_preservesExistingDataWhenTheSeasonHasNotBeenPlayedYet() {
        // ESPN answers for an upcoming season with a full set of all-zero rows, not with
        // nothing, so an off-by-one season would replace real stats with zeroes.
        when(client.fetchSeasonStats(2026)).thenReturn(aSeasonOf(List.of(
                new PlayerStatLine(1L, "Connor McDavid", "C", null, 0, 0, 0, null, 0)), 0));

        assertThatThrownBy(() -> service.sync())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no games played");

        verify(repository, never()).deleteAllInBatch();
        verify(repository, never()).saveAll(any());
    }

    @Test
    void players_reportsTheLatestSyncTime() {
        EspnPlayerStats row = new EspnPlayerStats();
        row.setId(1L);
        row.setFullName("Cale Makar");
        row.setPosition("D");
        row.setShifts(2137);
        row.setSyncedAt(Instant.parse("2026-08-10T07:45:00Z"));
        when(repository.findAll()).thenReturn(List.of(row));

        var response = service.players();

        assertThat(response.syncedAt()).isEqualTo(Instant.parse("2026-08-10T07:45:00Z"));
        assertThat(response.players()).singleElement()
                .satisfies(line -> assertThat(line.shifts()).isEqualTo(2137));
    }
}
