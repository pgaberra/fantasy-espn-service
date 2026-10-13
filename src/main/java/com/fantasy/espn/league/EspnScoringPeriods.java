package com.fantasy.espn.league;

import com.fantasy.espn.config.EspnProperties;
import com.fantasy.espn.exception.EspnLeagueNotFoundException;
import com.fantasy.espn.exception.EspnUpstreamException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * Which of ESPN's daily scoring periods a calendar day is, read off ESPN's own NHL schedule: every
 * game there is filed under the period it counts in and carries its start time, so a day's period
 * is the one its games are filed under. Nothing here depends on when ESPN rolls its "today" over
 * to the next period, or on our clock agreeing with ESPN's.
 *
 * <p>A day is the US Eastern calendar day. Every NHL game, those played abroad included, starts
 * on its listed date in Eastern time, none later than about 22:30; in UTC a late game would land
 * on the next day. Were ESPN's periods cut some other way, the games of one
 * Eastern day would be filed under two periods, and that day is refused as ESPN's fault rather
 * than answered with one of the two.
 */
@Component
public class EspnScoringPeriods {

    static final ZoneId ESPN_DAY = ZoneId.of("America/New_York");

    /** Postponed games move between periods during a season, so the schedule is re-read. */
    static final Duration MAX_AGE = Duration.ofHours(6);

    private final EspnFantasyClient client;
    private final Clock clock;
    private final int season;

    private volatile Calendar cached;

    public EspnScoringPeriods(EspnFantasyClient client, Clock clock, EspnProperties props) {
        this.client = client;
        this.clock = clock;
        this.season = props.season();
    }

    /**
     * The scoring period {@code date} is, in the configured season.
     *
     * @throws IllegalArgumentException when the day is outside the season, or is a day without
     *                                  NHL games that ESPN gives no period of its own
     */
    public int periodOn(LocalDate date) {
        Calendar calendar = calendar();
        if (date.isBefore(calendar.firstDay()) || date.isAfter(calendar.lastDay())) {
            throw new IllegalArgumentException("date " + date + " is outside the " + seasonLabel()
                    + " season, which ESPN scores from " + calendar.firstDay() + " to " + calendar.lastDay());
        }
        if (calendar.conflicting().contains(date)) {
            throw new EspnUpstreamException(
                    "ESPN's schedule files the games of " + date + " under more than one scoring period");
        }
        Integer period = calendar.periods().get(date);
        if (period != null) {
            return period;
        }
        // A day without games inside the season: it has a period of its own only where ESPN counts
        // every calendar day, which the game days either side of it show.
        Map.Entry<LocalDate, Integer> before = calendar.periods().lowerEntry(date);
        Map.Entry<LocalDate, Integer> after = calendar.periods().higherEntry(date);
        if (before != null && after != null
                && after.getValue() - before.getValue() == ChronoUnit.DAYS.between(before.getKey(), after.getKey())) {
            return before.getValue() + (int) ChronoUnit.DAYS.between(before.getKey(), date);
        }
        throw new IllegalArgumentException(
                "No NHL games are played on " + date + ", and ESPN keeps no scoring period for it");
    }

    private Calendar calendar() {
        Calendar current = cached;
        Instant now = clock.instant();
        if (current == null || current.fetchedAt().plus(MAX_AGE).isBefore(now)) {
            current = read(now);
            cached = current;
        }
        return current;
    }

    private Calendar read(Instant now) {
        JsonNode root;
        try {
            root = client.getProSchedule(season);
        } catch (EspnLeagueNotFoundException e) {
            throw new EspnUpstreamException("ESPN has no NHL schedule for season " + season, e);
        }
        NavigableMap<LocalDate, Integer> periods = new TreeMap<>();
        Set<LocalDate> conflicting = new HashSet<>();
        for (JsonNode club : root.path("settings").path("proTeams")) {
            for (Map.Entry<String, JsonNode> filed : club.path("proGamesByScoringPeriod").properties()) {
                Integer period = periodId(filed.getKey());
                if (period == null) {
                    continue;
                }
                for (JsonNode game : filed.getValue()) {
                    JsonNode start = game.path("date");
                    if (!start.isNumber()) {
                        continue;
                    }
                    LocalDate day = Instant.ofEpochMilli(start.asLong()).atZone(ESPN_DAY).toLocalDate();
                    Integer earlier = periods.putIfAbsent(day, period);
                    if (earlier != null && !earlier.equals(period)) {
                        conflicting.add(day);
                    }
                }
            }
        }
        if (periods.isEmpty()) {
            throw new EspnUpstreamException("ESPN returned no NHL games for season " + season);
        }
        return new Calendar(Collections.unmodifiableNavigableMap(periods), Set.copyOf(conflicting), now);
    }

    private static Integer periodId(String key) {
        try {
            int period = Integer.parseInt(key);
            return period > 0 ? period : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** ESPN's season id is the year a season ends in: 2027 is the 2026-27 season. */
    private String seasonLabel() {
        return (season - 1) + "-" + String.format(Locale.ROOT, "%02d", season % 100);
    }

    private record Calendar(NavigableMap<LocalDate, Integer> periods, Set<LocalDate> conflicting, Instant fetchedAt) {

        LocalDate firstDay() {
            return periods.firstKey();
        }

        LocalDate lastDay() {
            return periods.lastKey();
        }
    }
}
