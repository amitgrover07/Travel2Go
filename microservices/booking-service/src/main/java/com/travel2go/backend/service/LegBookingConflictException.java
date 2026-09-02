package com.travel2go.backend.service;

public class LegBookingConflictException extends RuntimeException {
    public LegBookingConflictException(String message) {
        super(message);
    }
}
