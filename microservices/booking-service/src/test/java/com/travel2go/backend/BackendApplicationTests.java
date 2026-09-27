package com.travel2go.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.travel2go.backend.repository.BookingRepository;
import com.travel2go.backend.repository.LeadRepository;

@SpringBootTest(properties = {
    "spring.cloud.gcp.firestore.enabled=false",
    "spring.cloud.gcp.storage.enabled=false",
    "spring.cloud.gcp.core.enabled=false"
})
@MockitoBean(types = {BookingRepository.class, LeadRepository.class})
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}


