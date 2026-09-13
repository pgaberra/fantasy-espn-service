package com.fantasy.espn.player;

import com.fantasy.espn.config.EspnProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The starting half of the sync. A trigger answers as soon as the work is under way, because
 * the work itself takes minutes and holding an HTTP request open for it is what made the
 * synchronous version fragile.
 */
class EspnPlayerSyncStartTest {

    private EspnPlayerClient client;
    private EspnPlayerSyncService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        client = mock(EspnPlayerClient.class);
        EspnHeadshotVerifier verifier = mock(EspnHeadshotVerifier.class);
        when(verifier.withVerifiedHeadshots(any())).thenAnswer(call -> call.getArgument(0));
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, Consumer.class).accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        service = new EspnPlayerSyncService(client, verifier, mock(EspnPlayerRepository.class),
                mock(EspnSkaterSeasonRepository.class), mock(EspnGoalieSeasonRepository.class),
                transactionTemplate,
                new EspnProperties("https://espn.test", "fhl", 2027, 2026, 2025, null));
    }

    @Test
    void startAsync_returnsBeforeTheWorkIsDone() throws Exception {
        CountDownLatch fetching = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(client.fetchPlayers(anyInt())).thenAnswer(call -> {
            fetching.countDown();
            release.await(5, TimeUnit.SECONDS);
            return List.of();
        });

        assertThat(service.startAsync()).isTrue();

        assertThat(fetching.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(service.isRunning()).isTrue();
        release.countDown();
        await().atMost(5, TimeUnit.SECONDS).until(() -> !service.isRunning());
    }

    /** Already running is an ordinary answer, not a fault — the caller is told, not thrown at. */
    @Test
    void startAsync_refusesASecondRunWhileOneIsUnderWay() throws Exception {
        CountDownLatch fetching = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(client.fetchPlayers(anyInt())).thenAnswer(call -> {
            fetching.countDown();
            release.await(5, TimeUnit.SECONDS);
            return List.of();
        });
        service.startAsync();
        assertThat(fetching.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(service.startAsync()).isFalse();

        release.countDown();
        await().atMost(5, TimeUnit.SECONDS).until(() -> !service.isRunning());
    }

    /**
     * The nightly run overlapping a triggered one. It used to throw "already running", which the
     * scheduler logged at ERROR as a failed sync although the triggered run was doing the work.
     */
    @Test
    void sync_whileARunIsUnderWay_isEmptyRatherThanAFailure() throws Exception {
        CountDownLatch fetching = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(client.fetchPlayers(anyInt())).thenAnswer(call -> {
            fetching.countDown();
            release.await(5, TimeUnit.SECONDS);
            return List.of();
        });
        service.startAsync();
        assertThat(fetching.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(service.sync()).isEmpty();

        release.countDown();
        await().atMost(5, TimeUnit.SECONDS).until(() -> !service.isRunning());
    }

    /**
     * A refused fetch is exactly what the guards are for, and it happens on a thread with no
     * request to fail. It must clear the flag regardless, or nothing can ever sync again.
     */
    @Test
    void aFailedRunStopsBeingRunning() {
        when(client.fetchPlayers(anyInt())).thenThrow(new IllegalStateException("ESPN unreachable"));

        assertThat(service.startAsync()).isTrue();

        await().atMost(5, TimeUnit.SECONDS).until(() -> !service.isRunning());
        assertThat(service.startAsync()).isTrue();
    }
}
