package com.travel2go.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaitlistWatchRequest {
    private String contact;   // optional
    private String channel;   // WHATSAPP | EMAIL | SMS | null
}
