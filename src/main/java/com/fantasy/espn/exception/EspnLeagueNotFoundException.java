package com.fantasy.espn.exception;

/** Raised when ESPN has no league for the given id + season (a 404 from ESPN). */
public class EspnLeagueNotFoundException extends RuntimeException {
    public EspnLeagueNotFoundException(String message) {
        super(message);
    }
}
