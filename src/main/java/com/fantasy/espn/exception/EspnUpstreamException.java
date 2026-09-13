package com.fantasy.espn.exception;

/**
 * ESPN itself failed: an error status with no meaning of its own, no answer at all, or a body
 * that is empty or not the document we read. Only this maps to 502, so a fault inside this
 * service (a missing encryption key, a bug) is never reported as an ESPN outage.
 */
public class EspnUpstreamException extends RuntimeException {

    public EspnUpstreamException(String message) {
        super(message);
    }

    public EspnUpstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
