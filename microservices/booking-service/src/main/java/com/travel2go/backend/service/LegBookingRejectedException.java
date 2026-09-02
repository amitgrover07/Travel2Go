package com.travel2go.backend.service;

public class LegBookingRejectedException extends RuntimeException {
    public LegBookingRejectedException(String message) {
        super(message);
    }
}
