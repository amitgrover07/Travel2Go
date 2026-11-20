package com.travel2go.agent.client;

import com.travel2go.agent.dto.AgentWaitlistRequest;
import com.travel2go.agent.dto.AgentWaitlistResponse;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class WaitlistClientFallback implements FallbackFactory<WaitlistClient> {

    @Override
    public WaitlistClient create(Throwable cause) {
        return new WaitlistClient() {
            @Override
            public AgentWaitlistResponse check(AgentWaitlistRequest request) {
                throw new UpstreamUnavailableException("waitlist-service unavailable", cause);
            }
        };
    }
}
