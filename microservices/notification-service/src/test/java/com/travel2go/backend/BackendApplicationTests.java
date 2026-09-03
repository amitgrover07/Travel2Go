package com.travel2go.backend;

import com.google.cloud.storage.Storage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
@MockBean(Storage.class)
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
