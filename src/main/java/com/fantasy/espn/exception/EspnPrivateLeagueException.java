package com.fantasy.espn.exception;

/**
 * Raised when ESPN refuses to serve a league because it is private and the request carried
 * no (or invalid) {@code espn_s2} / {@code SWID} cookies. Surfaced as a 400 so the BFF relays
 * it to the web as an actionable client outcome ("this league is private — check your league
 * id and ESPN cookies"), not a 5xx.
 */
public class EspnPrivateLeagueException extends RuntimeException {
    public EspnPrivateLeagueException(String message) {
        super(message);
    }
}
