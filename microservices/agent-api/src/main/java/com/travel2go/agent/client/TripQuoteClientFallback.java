package com.travel2go.agent.client;

import com.travel2go.backend.dto.LegQuoteResponse;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class TripQuoteClientFallback implements FallbackFactory<TripQuoteClient> {

    @Override
    public TripQuoteClient create(Throwable cause) {
        return legId -> {
            throw new UpstreamUnavailableException("trip-service unavailable", cause);
        };
    }
}
