package com.fantasy.espn.credential;

/** A user's decrypted ESPN cookies, used to read their private leagues. */
public record EspnCookies(String espnS2, String swid) {
}
