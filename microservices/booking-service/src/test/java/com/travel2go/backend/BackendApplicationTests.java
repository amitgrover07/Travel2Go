package com.travel2go.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.travel2go.backend.repository.BookingRepository;
import com.travel2go.backend.repository.LeadRepository;

@SpringBootTest(properties = {
    "spring.cloud.gcp.firestore.enabled=false",
    "spring.cloud.gcp.storage.enabled=false",
    "spring.cloud.gcp.core.enabled=false"
})
@MockBean({BookingRepository.class, LeadRepository.class})
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}


