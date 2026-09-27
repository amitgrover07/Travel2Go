package com.travel2go.backend;

import com.travel2go.backend.repository.LegRepository;
import com.travel2go.backend.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
    "spring.cloud.gcp.firestore.enabled=false",
    "spring.cloud.gcp.storage.enabled=false",
    "spring.cloud.gcp.core.enabled=false"
})
@MockitoBean(types = {TripRepository.class, LegRepository.class})
class TripServiceApplicationTests {

	@Test
	void contextLoads() {
	}
}
