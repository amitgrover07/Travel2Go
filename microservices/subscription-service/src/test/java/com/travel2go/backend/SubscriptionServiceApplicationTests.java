package com.travel2go.backend;

import com.travel2go.backend.repository.CouponRepository;
import com.travel2go.backend.repository.PlanRepository;
import com.travel2go.backend.repository.SubscriptionChargeRepository;
import com.travel2go.backend.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude="
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration,"
        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
    "jwt.secret=test-jwt-secret-0123456789abcdef0123456789abcdef"
})
@MockitoBean(types = {PlanRepository.class, CouponRepository.class,
        SubscriptionRepository.class, SubscriptionChargeRepository.class})
class SubscriptionServiceApplicationTests {

	@Test
	void contextLoads() {
	}
}
