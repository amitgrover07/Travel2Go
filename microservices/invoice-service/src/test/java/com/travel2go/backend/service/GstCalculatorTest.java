package com.travel2go.backend.service;

import com.travel2go.backend.service.GstCalculator.GstBreakdown;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tax-split + reconciliation (F7), pure logic. Place of supply drives intra- vs
 * inter-state; taxable + tax always equals the charged amount.
 */
class GstCalculatorTest {

    private final GstCalculator calculator = new GstCalculator();

    @Test
    void intraStateSupply_splitsIntoCgstAndSgst_noIgst() {
        GstBreakdown b = calculator.compute(105_000L, 500, "07", "07"); // 5% inclusive, same state
        assertThat(b.taxableValuePaise()).isEqualTo(100_000L);
        assertThat(b.cgstPaise()).isEqualTo(2_500L);
        assertThat(b.sgstPaise()).isEqualTo(2_500L);
        assertThat(b.igstPaise()).isZero();
        assertThat(b.intraState()).isTrue();
    }

    @Test
    void interStateSupply_isSingleIgst_noCgstSgst() {
        GstBreakdown b = calculator.compute(105_000L, 500, "07", "27"); // seller Delhi, supply Maharashtra
        assertThat(b.taxableValuePaise()).isEqualTo(100_000L);
        assertThat(b.igstPaise()).isEqualTo(5_000L);
        assertThat(b.cgstPaise()).isZero();
        assertThat(b.sgstPaise()).isZero();
        assertThat(b.intraState()).isFalse();
    }

    @Test
    void totalsAlwaysReconcileToTheChargedAmount() {
        for (long charged : new long[]{1L, 99L, 100_001L, 149_999L, 123_457L, 5_00_000L}) {
            for (String pos : new String[]{"07", "27"}) {
                GstBreakdown b = calculator.compute(charged, 500, "07", pos);
                assertThat(b.taxableValuePaise() + b.cgstPaise() + b.sgstPaise() + b.igstPaise())
                        .as("reconcile charged=%d pos=%s", charged, pos)
                        .isEqualTo(charged);
                assertThat(b.totalPaise()).isEqualTo(charged);
            }
        }
    }

    @Test
    void oddPaiseTax_stillSplitsExactlyBetweenCgstAndSgst() {
        // Choose a charge whose tax is an odd number of paise.
        GstBreakdown b = calculator.compute(105_001L, 500, "07", "07");
        assertThat(b.cgstPaise() + b.sgstPaise()).isEqualTo(b.totalPaise() - b.taxableValuePaise());
        assertThat(Math.abs(b.cgstPaise() - b.sgstPaise())).isLessThanOrEqualTo(1L);
    }
}
