package com.travel2go.backend;

import com.travel2go.backend.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = {
    "spring.cloud.gcp.firestore.enabled=false",
    "spring.cloud.gcp.storage.enabled=false",
    "spring.cloud.gcp.core.enabled=false"
})
@MockBean({PaymentRepository.class})
class PaymentServiceApplicationTests {

	@Test
	void contextLoads() {
	}
}
