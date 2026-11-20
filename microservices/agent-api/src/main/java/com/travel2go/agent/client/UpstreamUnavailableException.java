package com.travel2go.agent.client;

/** An upstream service could not be reached (circuit open / timeout) - maps to HTTP 503. */
public class UpstreamUnavailableException extends RuntimeException {
    public UpstreamUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
