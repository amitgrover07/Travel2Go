package com.travel2go.backend.service;

/** Bad client input (maps to HTTP 400), distinct from a not-found shareToken (404). */
public class InvalidWaitlistInputException extends RuntimeException {
    public InvalidWaitlistInputException(String message) {
        super(message);
    }
}
