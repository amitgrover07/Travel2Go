package com.travel2go.backend;

import com.travel2go.backend.repository.WaitlistCheckRepository;
import com.travel2go.backend.repository.WaitlistWatchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
    "spring.cloud.gcp.firestore.enabled=false",
    "spring.cloud.gcp.storage.enabled=false",
    "spring.cloud.gcp.core.enabled=false"
})
@MockitoBean(types = {WaitlistCheckRepository.class, WaitlistWatchRepository.class})
class WaitlistServiceApplicationTests {

	@Test
	void contextLoads() {
	}
}
