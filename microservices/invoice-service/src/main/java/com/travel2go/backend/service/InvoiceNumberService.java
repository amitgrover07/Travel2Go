package com.travel2go.backend.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Hands out gapless, monotonic invoice numbers per financial year (Apr-Mar). The
 * atomic UPSERT ... RETURNING allocates each sequence value exactly once under
 * concurrency, with no gaps.
 */
@Service
@RequiredArgsConstructor
public class InvoiceNumberService {

    @PersistenceContext
    private EntityManager entityManager;

    /** @return the next invoice number for the given FY, e.g. T2G/2026-27/000001. */
    @Transactional
    public String nextNumber(String fy) {
        Number value = (Number) entityManager.createNativeQuery(
                        "INSERT INTO invoice_sequences(fy, next_val) VALUES (:fy, 1) "
                                + "ON CONFLICT (fy) DO UPDATE SET next_val = invoice_sequences.next_val + 1 "
                                + "RETURNING next_val")
                .setParameter("fy", fy)
                .getSingleResult();
        return String.format("T2G/%s/%06d", fy, value.longValue());
    }

    /** Indian financial year label for a date (Apr 1 - Mar 31), e.g. 2026-27. */
    public static String financialYear(LocalDate date) {
        int startYear = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return String.format("%d-%02d", startYear, (startYear + 1) % 100);
    }
}
