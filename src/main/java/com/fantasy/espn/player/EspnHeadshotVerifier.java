package com.fantasy.espn.player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Asks ESPN's image CDN which players it actually has a portrait for.
 *
 * <p>The headshot URL is built from the player's id, so one can be produced for anybody — but
 * ESPN has no picture for roughly one player in seven and answers those with a 404. Handing out
 * a URL for them puts a broken image in the app, where a player with no headshot at all would
 * have drawn the placeholder, so the ones that do not resolve are dropped here.
 *
 * <p>Only a definite 404 drops a URL. A timeout, a refusal, anything else — the headshot stays:
 * the check is a courtesy, and a rate-limited sync must not be able to strip the whole pool of
 * its pictures.
 *
 * <p>Nothing is checked while the app shows no pictures ({@code players.avatars.enabled}, the same
 * {@code PLAYER_AVATARS_ENABLED} the BFF reads, off by default). They are ESPN's photographs and we
 * hold no licence to show them, so there is no reason to ask ESPN's CDN about them either; the BFF
 * strips every headshot on the way out, and the URLs are stored unchecked. There is deliberately no
 * switch of this check's own: whether we touch ESPN's pictures is one decision, not two.
 */
@Component
public class EspnHeadshotVerifier {

    private static final Logger log = LoggerFactory.getLogger(EspnHeadshotVerifier.class);

    /**
     * Enough to get through the pool in seconds, few enough to be a polite neighbour to a CDN
     * that owes us nothing — and past which it starts refusing, which teaches us nothing.
     */
    private static final int CONCURRENT_CHECKS = 8;

    private final RestClient imageClient;
    private final boolean avatarsEnabled;

    public EspnHeadshotVerifier(RestClient espnImageRestClient,
                                @Value("${players.avatars.enabled:false}") boolean avatarsEnabled) {
        this.imageClient = espnImageRestClient;
        this.avatarsEnabled = avatarsEnabled;
    }

    /**
     * The players as they came in, minus the headshots ESPN has no picture behind — or exactly as
     * they came in, without a single request to the CDN, where avatars are off.
     */
    public List<FetchedPlayer> withVerifiedHeadshots(List<FetchedPlayer> players) {
        if (!avatarsEnabled) {
            return players;
        }
        Set<Long> missing = idsWithoutAPortrait(players);
        if (missing.isEmpty()) {
            return players;
        }
        log.info("Headshot check: {} of {} players have no portrait at ESPN",
                missing.size(), players.size());
        return players.stream()
                .map(player -> missing.contains(player.id()) ? withoutHeadshot(player) : player)
                .toList();
    }

    private Set<Long> idsWithoutAPortrait(List<FetchedPlayer> players) {
        List<FetchedPlayer> toCheck = players.stream()
                .filter(player -> player.headshot() != null)
                .toList();
        // A small fixed pool rather than a thread per player: the point is to bound how hard the
        // CDN is hit, and these calls block on IO anyway.
        try (ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_CHECKS)) {
            List<Future<Long>> checks = toCheck.stream()
                    .map(player -> executor.submit(
                            () -> resolves(player.headshot()) ? null : player.id()))
                    .toList();
            Set<Long> missing = new HashSet<>();
            for (Future<Long> check : checks) {
                Long absent = await(check);
                if (absent != null) {
                    missing.add(absent);
                }
            }
            return missing;
        }
    }

    private boolean resolves(String url) {
        try {
            Boolean found = imageClient.head()
                    .uri(URI.create(url))
                    .exchange((request, response) ->
                            response.getStatusCode().value() != HttpStatus.NOT_FOUND.value());
            return found == null || found;
        } catch (Exception anythingButAClearNo) {
            return true;
        }
    }

    private static Long await(Future<Long> check) {
        try {
            return check.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while checking headshots", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to check a headshot", e);
        }
    }

    private static FetchedPlayer withoutHeadshot(FetchedPlayer player) {
        return new FetchedPlayer(player.id(), player.firstName(), player.lastName(),
                player.fullName(), player.position(), player.eligiblePositions(),
                player.sweaterNumber(), player.teamAbbrev(), null, player.active(),
                player.goalie(), new ArrayList<>(player.skaterSeasons()),
                new ArrayList<>(player.goalieSeasons()));
    }
}
