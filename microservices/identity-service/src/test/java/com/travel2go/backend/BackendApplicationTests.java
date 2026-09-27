package com.travel2go.backend;

import com.travel2go.backend.repository.BookingRepository;
import com.travel2go.backend.repository.GlobalSettingsRepository;
import com.travel2go.backend.repository.HolidayPackageRepository;
import com.travel2go.backend.repository.UserRepository;
import com.travel2go.backend.repository.VerificationCodeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
    "spring.cloud.gcp.firestore.enabled=false",
    "spring.cloud.gcp.storage.enabled=false",
    "spring.cloud.gcp.core.enabled=false",
    "NOTIFICATION_SERVICE_URL=http://test-notification"
})
@MockitoBean(types = {BookingRepository.class, GlobalSettingsRepository.class, HolidayPackageRepository.class,
        UserRepository.class, VerificationCodeRepository.class})
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
