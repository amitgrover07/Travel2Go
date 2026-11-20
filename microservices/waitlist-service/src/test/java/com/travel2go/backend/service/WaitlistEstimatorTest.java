package com.travel2go.backend.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boundary + monotonicity tests for the transparent estimator (F1).
 * Pure logic, no Spring context.
 */
class WaitlistEstimatorTest {

    private final WaitlistEstimator estimator = new WaitlistEstimator();

    @Test
    void probabilityAlwaysInUnitInterval() {
        for (int position : new int[]{1, 5, 20, 80, 300}) {
            for (long days : new long[]{0, 1, 7, 45, 120}) {
                for (String cls : new String[]{"SL", "3A", "2A", "1A", "2S", "CC", "ZZ", null}) {
                    var e = estimator.estimate("12345", LocalDate.now().plusDays(days), cls, position);
                    assertThat(e.probability())
                            .as("class=%s days=%d pos=%d", cls, days, position)
                            .isBetween(0.0, 1.0);
                }
            }
        }
    }

    @Test
    void alwaysOneOfThreeVerdicts() {
        var e = estimator.estimate("12345", LocalDate.now().plusDays(10), "SL", 15);
        assertThat(e.verdict()).isIn(
                WaitlistEstimator.VERDICT_LIKELY,
                WaitlistEstimator.VERDICT_BORDERLINE,
                WaitlistEstimator.VERDICT_UNLIKELY);
        assertThat(e.explanation()).isNotBlank();
    }

    @Test
    void frontOfQueueFarOutSleeperIsLikely() {
        var e = estimator.estimate("12345", LocalDate.now().plusDays(30), "SL", 2);
        assertThat(e.probability()).isGreaterThanOrEqualTo(0.66);
        assertThat(e.verdict()).isEqualTo(WaitlistEstimator.VERDICT_LIKELY);
    }

    @Test
    void deepPositionSameDayIsUnlikely() {
        var e = estimator.estimate("12345", LocalDate.now(), "1A", 200);
        assertThat(e.probability()).isLessThan(0.40);
        assertThat(e.verdict()).isEqualTo(WaitlistEstimator.VERDICT_UNLIKELY);
    }

    @Test
    void pastDateCannotClear() {
        var e = estimator.estimate("12345", LocalDate.now().minusDays(1), "SL", 5);
        assertThat(e.probability()).isEqualTo(0.0);
        assertThat(e.verdict()).isEqualTo(WaitlistEstimator.VERDICT_UNLIKELY);
    }

    @Test
    void nonWaitlistPositionIsEffectivelyConfirmed() {
        var e = estimator.estimate("12345", LocalDate.now().plusDays(3), "3A", 0);
        assertThat(e.probability()).isEqualTo(1.0);
        assertThat(e.verdict()).isEqualTo(WaitlistEstimator.VERDICT_LIKELY);
    }

    @Test
    void moreDaysToDepartureNeverLowersProbability() {
        double near = estimator.estimate("12345", LocalDate.now().plusDays(2), "3A", 20).probability();
        double far = estimator.estimate("12345", LocalDate.now().plusDays(40), "3A", 20).probability();
        assertThat(far).isGreaterThanOrEqualTo(near);
    }

    @Test
    void worsePositionNeverRaisesProbability() {
        double better = estimator.estimate("12345", LocalDate.now().plusDays(15), "SL", 5).probability();
        double worse = estimator.estimate("12345", LocalDate.now().plusDays(15), "SL", 40).probability();
        assertThat(worse).isLessThanOrEqualTo(better);
    }
}
