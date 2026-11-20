package com.travel2go.agent;

import com.travel2go.agent.client.BookingStatusClient;
import com.travel2go.agent.client.TripQuoteClient;
import com.travel2go.agent.client.WaitlistClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class AgentApiApplicationTests {

	@MockitoBean private WaitlistClient waitlistClient;
	@MockitoBean private BookingStatusClient bookingStatusClient;
	@MockitoBean private TripQuoteClient tripQuoteClient;

	@Test
	void contextLoads() {
	}
}
