package com.travel2go.backend.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Transparent, public-data-only waitlist clearance estimator (F1).
 *
 * <p>Deliberately a heuristic, not a black box: the confirmation probability is a
 * bounded, explainable function of the three public inputs a traveller already
 * knows - current waitlist position, how far out the journey is, and the travel
 * class. No authenticated IRCTC access is required or used, so it works pre-launch
 * and for non-registered users (per the IRCTC-independence risk in the PRD).
 *
 * <p>Intuition behind the model:
 * <ul>
 *   <li><b>Position</b> - the lower the current WL number, the closer to the front
 *       of the queue; each class has a different typical volume of WL seats that
 *       eventually clear, so the same raw number means different things per class.</li>
 *   <li><b>Days to departure</b> - cancellations accrue over time, so a far-out
 *       journey has more runway to clear than a same-day one.</li>
 *   <li><b>Class turnover</b> - higher-churn classes (Sleeper, Second Sitting)
 *       clear waitlists more readily than low-churn premium classes (1A/2A).</li>
 * </ul>
 *
 * The model can be replaced later by one trained on historical clearance data
 * without changing the public contract.
 */
@Component
public class WaitlistEstimator {

    /** Verdict bands over the [0,1] probability. */
    public static final String VERDICT_LIKELY = "likely to clear";
    public static final String VERDICT_BORDERLINE = "borderline";
    public static final String VERDICT_UNLIKELY = "unlikely";

    private static final double LIKELY_THRESHOLD = 0.66;
    private static final double BORDERLINE_THRESHOLD = 0.40;

    /** How far out (days) saturates the "runway to clear" contribution. */
    private static final int DAYS_HORIZON = 45;

    public Estimate estimate(String trainNo, LocalDate travelDate, String classCode, int currentPosition) {
        String normalisedClass = classCode == null ? "" : classCode.trim().toUpperCase(Locale.ROOT);

        if (currentPosition <= 0) {
            // Not actually waitlisted (already confirmed / RAC / invalid) - treat as certain.
            return new Estimate(1.0, VERDICT_LIKELY,
                    "Position " + currentPosition + " is not a waitlist position, so the ticket is effectively confirmed.");
        }

        long daysToDeparture = ChronoUnit.DAYS.between(LocalDate.now(), travelDate);
        if (daysToDeparture < 0) {
            return new Estimate(0.0, VERDICT_UNLIKELY,
                    "The travel date has already passed, so this waitlist can no longer clear.");
        }

        int clearableCapacity = clearableCapacityForClass(normalisedClass);
        double classFactor = turnoverFactorForClass(normalisedClass);

        // Position score: 1.0 at the front of the queue, decaying to 0 as the position
        // approaches the class's typical clearable volume.
        double positionScore = clamp(1.0 - ((double) (currentPosition - 1) / clearableCapacity), 0.0, 1.0);

        // Days score: 0 same-day, saturating to 1.0 at the horizon.
        double daysScore = clamp((double) daysToDeparture / DAYS_HORIZON, 0.0, 1.0);

        double probability = clamp(classFactor * (0.6 * positionScore + 0.4 * daysScore), 0.0, 1.0);
        String verdict = verdictFor(probability);

        String explanation = String.format(Locale.ROOT,
                "At waitlist position %d in class %s with %d day%s to departure, roughly %.0f%% of comparable waitlists clear - %s.",
                currentPosition,
                normalisedClass.isEmpty() ? "(unspecified)" : normalisedClass,
                daysToDeparture,
                daysToDeparture == 1 ? "" : "s",
                probability * 100.0,
                verdict);

        return new Estimate(probability, verdict, explanation);
    }

    private String verdictFor(double probability) {
        if (probability >= LIKELY_THRESHOLD) {
            return VERDICT_LIKELY;
        }
        if (probability >= BORDERLINE_THRESHOLD) {
            return VERDICT_BORDERLINE;
        }
        return VERDICT_UNLIKELY;
    }

    /** Typical volume of WL seats that eventually clear for a class (public-pattern approximation). */
    private int clearableCapacityForClass(String classCode) {
        return switch (classCode) {
            case "SL" -> 60;   // Sleeper
            case "2S" -> 50;   // Second Sitting
            case "CC" -> 30;   // AC Chair Car
            case "3A", "3E" -> 25; // AC 3-tier / 3-economy
            case "2A" -> 12;   // AC 2-tier
            case "1A" -> 6;    // AC First
            default -> 30;
        };
    }

    /** Relative churn quality of a class - higher classes turn over less. */
    private double turnoverFactorForClass(String classCode) {
        return switch (classCode) {
            case "SL", "2S" -> 1.0;
            case "CC", "3A", "3E" -> 0.9;
            case "2A" -> 0.8;
            case "1A" -> 0.7;
            default -> 0.85;
        };
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Immutable estimator output. */
    public record Estimate(double probability, String verdict, String explanation) {
    }
}
