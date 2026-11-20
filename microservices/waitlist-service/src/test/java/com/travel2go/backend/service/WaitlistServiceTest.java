package com.travel2go.backend.service;

import com.travel2go.backend.dto.WaitlistCheckRequest;
import com.travel2go.backend.model.WaitlistCheck;
import com.travel2go.backend.repository.WaitlistCheckRepository;
import com.travel2go.backend.repository.WaitlistWatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaitlistServiceTest {

    @Mock private WaitlistCheckRepository checkRepository;
    @Mock private WaitlistWatchRepository watchRepository;

    private WaitlistService service;

    @BeforeEach
    void setUp() {
        service = new WaitlistService(new WaitlistEstimator(), checkRepository, watchRepository);
        ReflectionTestUtils.setField(service, "shareBaseUrl", "https://travel2go.in/waitlist");
        ReflectionTestUtils.setField(service, "ipHashSecret", "test-ip-hash-secret-at-least-32-bytes-long");
        service.validateSecret();
    }

    private WaitlistCheckRequest validRequest() {
        return WaitlistCheckRequest.builder()
                .trainNo("12345").travelDate("2026-12-25").classCode("SL").currentPosition(10).build();
    }

    @Test
    void check_persistsHmacHashOfIp_deterministicPerIp() {
        ArgumentCaptor<WaitlistCheck> captor = ArgumentCaptor.forClass(WaitlistCheck.class);
        when(checkRepository.save(captor.capture())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        service.check(validRequest(), "203.0.113.42");
        service.check(validRequest(), "203.0.113.42");
        service.check(validRequest(), "198.51.100.7");

        var saved = captor.getAllValues();
        String hashA1 = saved.get(0).getSourceIp();
        String hashA2 = saved.get(1).getSourceIp();
        String hashB = saved.get(2).getSourceIp();

        assertThat(hashA1).isNotEqualTo("203.0.113.42");        // never the raw address
        assertThat(hashA1).hasSize(64).matches("[0-9a-f]+");    // HMAC-SHA256 hex
        assertThat(hashA1).isEqualTo(hashA2);                   // same IP -> same hash
        assertThat(hashB).isNotEqualTo(hashA1);                 // different IP -> different hash
    }

    @Test
    void check_rejectsMalformedTrainNo() {
        assertThatThrownBy(() -> service.check(
                WaitlistCheckRequest.builder().trainNo("' OR 1=1").travelDate("2026-12-25")
                        .classCode("SL").currentPosition(5).build(), "1.2.3.4"))
                .isInstanceOf(InvalidWaitlistInputException.class);
    }

    @Test
    void check_rejectsOutOfRangePosition() {
        assertThatThrownBy(() -> service.check(
                WaitlistCheckRequest.builder().trainNo("12345").travelDate("2026-12-25")
                        .classCode("SL").currentPosition(10_000_000).build(), "1.2.3.4"))
                .isInstanceOf(InvalidWaitlistInputException.class);
    }

    @Test
    void watch_rejectsUnknownChannel() {
        assertThatThrownBy(() -> service.watch("tok", "a@b.com", "CARRIER_PIGEON"))
                .isInstanceOf(InvalidWaitlistInputException.class);
    }

    @Test
    void watch_rejectsOversizedContact() {
        String huge = "x".repeat(200);
        assertThatThrownBy(() -> service.watch("tok", huge, "EMAIL"))
                .isInstanceOf(InvalidWaitlistInputException.class);
    }

    @Test
    void getByShareToken_throwsWhenMissing() {
        when(checkRepository.findByShareToken("missing")).thenReturn(Mono.empty());
        assertThatThrownBy(() -> service.getByShareToken("missing"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateSecret_rejectsWeakKey() {
        WaitlistService weak = new WaitlistService(new WaitlistEstimator(), checkRepository, watchRepository);
        ReflectionTestUtils.setField(weak, "ipHashSecret", "short");
        assertThatThrownBy(weak::validateSecret).isInstanceOf(IllegalStateException.class);
    }
}
