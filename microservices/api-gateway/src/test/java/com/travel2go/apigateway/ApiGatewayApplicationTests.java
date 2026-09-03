package com.travel2go.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "IDENTITY_SERVICE_URL=http://test-identity",
    "PACKAGE_SERVICE_URL=http://test-package",
    "BOOKING_SERVICE_URL=http://test-booking",
    "MEDIA_SERVICE_URL=http://test-media",
    "TRIP_SERVICE_URL=http://test-trip",
    "PAYMENT_SERVICE_URL=http://test-payment"
})
class ApiGatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}
