package com.travel2go.backend;

import com.travel2go.backend.repository.BookingRepository;
import com.travel2go.backend.repository.GlobalSettingsRepository;
import com.travel2go.backend.repository.HolidayPackageRepository;
import com.travel2go.backend.repository.UserRepository;
import com.travel2go.backend.repository.VerificationCodeRepository;
import com.travel2go.backend.service.GcsStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = {
    "spring.cloud.gcp.firestore.enabled=false",
    "spring.cloud.gcp.storage.enabled=false",
    "spring.cloud.gcp.core.enabled=false"
})
@MockBean({BookingRepository.class, GlobalSettingsRepository.class, HolidayPackageRepository.class,
        UserRepository.class, VerificationCodeRepository.class, GcsStorageService.class})
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
