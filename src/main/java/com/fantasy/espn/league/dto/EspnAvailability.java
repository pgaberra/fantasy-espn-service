package com.fantasy.espn.league.dto;

/**
 * How a player who is not on a roster can be picked up. The distinction is the manager's whole
 * decision on a Tuesday night: a free agent is in the lineup tonight, a player on waivers is not.
 */
public enum EspnAvailability {
    FREE_AGENT,
    WAIVERS,
    UNKNOWN;

    /** ESPN's own status wording. Anything else is UNKNOWN rather than a guess. */
    public static EspnAvailability of(String status) {
        if (status == null) {
            return UNKNOWN;
        }
        return switch (status) {
            case "FREEAGENT" -> FREE_AGENT;
            case "WAIVERS" -> WAIVERS;
            default -> UNKNOWN;
        };
    }
}
