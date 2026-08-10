package com.fantasy.espn.player;

import com.fantasy.espn.config.EspnProperties;
import com.fantasy.espn.player.dto.PlayerStatLine;
import com.fantasy.espn.player.dto.PlayerSyncResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

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
                new EspnProperties("https://espn.test", "fhl", 2026, 2026, null));
    }

    @Test
    void sync_storesFetchedStatLines() {
        when(client.fetchSeasonStats(2026)).thenReturn(List.of(
                new PlayerStatLine(1L, "Connor McDavid", "C", 67, 2, 1413, null, 88566)));

        PlayerSyncResponse result = service.sync();

        assertThat(result.players()).isEqualTo(1);
        ArgumentCaptor<List<EspnPlayerStats>> saved = ArgumentCaptor.captor();
        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement()
                .satisfies(row -> {
                    assertThat(row.getFullName()).isEqualTo("Connor McDavid");
                    assertThat(row.getHatTricks()).isEqualTo(2);
                    assertThat(row.getSyncedAt()).isNotNull();
                });
    }

    @Test
    void sync_keepsTheDuplicateThatActuallyPlayed() {
        when(client.fetchSeasonStats(2026)).thenReturn(List.of(
                new PlayerStatLine(1L, "Matt Murray", "G", 0, null, null, 0, 0),
                new PlayerStatLine(2L, "Matt Murray", "G", 5, null, null, 1, 18000)));

        service.sync();

        ArgumentCaptor<List<EspnPlayerStats>> saved = ArgumentCaptor.captor();
        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement()
                .satisfies(row -> assertThat(row.getId()).isEqualTo(2L));
    }

    @Test
    void sync_preservesExistingDataWhenEspnReturnsNothing() {
        when(client.fetchSeasonStats(2026)).thenReturn(List.of());

        assertThatThrownBy(() -> service.sync()).isInstanceOf(IllegalStateException.class);

        verify(repository, never()).deleteAllInBatch();
        verify(repository, never()).saveAll(any());
    }

    @Test
    void sync_preservesExistingDataWhenTheSeasonHasNotBeenPlayedYet() {
        // ESPN answers for an upcoming season with a full set of all-zero rows, not with
        // nothing, so an off-by-one season would replace real stats with zeroes.
        when(client.fetchSeasonStats(2026)).thenReturn(List.of(
                new PlayerStatLine(1L, "Connor McDavid", "C", 0, 0, 0, null, 0),
                new PlayerStatLine(2L, "Cale Makar", "D", 0, 0, 0, null, 0)));

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
        row.setSyncedAt(java.time.Instant.parse("2026-08-10T07:45:00Z"));
        when(repository.findAll()).thenReturn(List.of(row));

        var response = service.players();

        assertThat(response.syncedAt()).isEqualTo(java.time.Instant.parse("2026-08-10T07:45:00Z"));
        assertThat(response.players()).singleElement()
                .satisfies(line -> assertThat(line.shifts()).isEqualTo(2137));
    }
}
