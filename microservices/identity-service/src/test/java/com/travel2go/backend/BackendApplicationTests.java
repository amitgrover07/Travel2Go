package com.travel2go.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
	"spring.cloud.gcp.firestore.emulator.enabled=true",
	"spring.cloud.gcp.firestore.host-port=localhost:8080",
	"spring.cloud.gcp.project-id=test-project"
})
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
