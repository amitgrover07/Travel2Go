package com.travel2go.backend.dto;

import com.travel2go.backend.model.Booking;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingEvent {
    private String eventId;
    private String eventType;
    private long timestamp;
    private Booking booking;
}
