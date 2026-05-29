package com.example.athletehub.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pure functions for the body-fat formulas server-side. Kept Spring-free
 * so the math is easy to unit-test without booting a context.
 *
 * <h2>Formulas</h2>
 * <ul>
 *   <li><b>Jackson-Pollock 7-site (Siri)</b> — sums chest + abdomen +
 *       thigh + tricep + subscapular + suprailiac + midaxillary
 *       skinfolds (mm), plugs the sum into the sex-specific body-density
 *       equation, then Siri converts density to body fat %.</li>
 *   <li><b>Navy (Hodgdon-Beckett, cm version)</b> — uses neck + waist
 *       circumferences (men), plus hip for women, with height in cm.</li>
 * </ul>
 *
 * <p>Durnin & Womersley 1974 has age-bracket coefficients that bloat the
 * code without much MVP value — left as a follow-up so clients sending
 * {@code bfMethod = "durnin"} currently get 400 from the service layer.
 *
 * <p>All outputs are clamped to [0, 100] and scaled to 2 decimal places
 * for BigDecimal round-trip stability with the {@code body_fat_pct
 * NUMERIC(5,2)} column.
 */
final class BodyFatCalculator {

    private BodyFatCalculator() {}

    // ── Jackson-Pollock 7-site ───────────────────────────────────────────

    /** Skinfold point ids required by the J-P 7 formula. */
    static final Set<String> JP7_REQUIRED_POINTS = Set.of(
            "chest", "abdomen", "thigh", "tricep",
            "subscapular", "suprailiac", "midaxillary");

    /**
     * @param skinfoldsMm map of point id → skinfold thickness in mm
     * @param sex "male" or "female"
     * @param age in years
     * @return body fat % (Siri equation), clamped + scaled.
     * @throws IllegalArgumentException when any required skinfold is missing.
     */
    static BigDecimal jacksonPollock7(Map<String, BigDecimal> skinfoldsMm, String sex, int age) {
        for (String point : JP7_REQUIRED_POINTS) {
            if (!skinfoldsMm.containsKey(point)) {
                throw new IllegalArgumentException("missing skinfold: " + point);
            }
        }
        double sum = 0;
        for (String point : JP7_REQUIRED_POINTS) {
            sum += skinfoldsMm.get(point).doubleValue();
        }
        double bd;
        if ("male".equals(sex)) {
            bd = 1.112
                    - 0.00043499 * sum
                    + 0.00000055 * sum * sum
                    - 0.00028826 * age;
        } else if ("female".equals(sex)) {
            bd = 1.097
                    - 0.00046971 * sum
                    + 0.00000056 * sum * sum
                    - 0.00012828 * age;
        } else {
            throw new IllegalArgumentException("sex must be male or female");
        }
        return siriClampScale(bd);
    }

    // ── Navy (Hodgdon-Beckett, cm) ──────────────────────────────────────

    /** Circumference point ids required by Navy for each sex. */
    static Set<String> navyRequiredPoints(String sex) {
        if ("male".equals(sex)) return Set.of("neck", "waist");
        if ("female".equals(sex)) return Set.of("neck", "waist", "hip");
        throw new IllegalArgumentException("sex must be male or female");
    }

    /**
     * @param circumferencesCm map of point id → circumference in cm
     * @param sex "male" or "female"
     * @param heightCm
     * @return body fat % (Hodgdon-Beckett body-density form + Siri),
     *         clamped + scaled.
     *
     * <p>The Hodgdon-Beckett formula has two flavors: one with coefficients
     * calibrated for inches (the original Navy form), and a cm form that
     * folds straight into Siri. Mixing the two — using inch coefficients
     * with cm inputs — overshoots wildly (e.g. 50%+ for a normal-bodied
     * woman), so this implementation uses the cm form throughout. The
     * expression below acts as the body-density input to Siri directly:
     * <pre>
     *   BF% = 495 / D − 450
     *
     *   D(male)   = 1.0324  − 0.19077·log10(waist − neck) + 0.15456·log10(height)
     *   D(female) = 1.29579 − 0.35004·log10(waist + hip − neck) + 0.22100·log10(height)
     * </pre>
     */
    static BigDecimal navy(Map<String, BigDecimal> circumferencesCm, String sex, BigDecimal heightCm) {
        Set<String> required = navyRequiredPoints(sex);
        for (String point : required) {
            if (!circumferencesCm.containsKey(point)) {
                throw new IllegalArgumentException("missing circumference: " + point);
            }
        }
        double neck = circumferencesCm.get("neck").doubleValue();
        double waist = circumferencesCm.get("waist").doubleValue();
        double height = heightCm.doubleValue();

        double density;
        if ("male".equals(sex)) {
            double waistMinusNeck = waist - neck;
            if (waistMinusNeck <= 0) {
                throw new IllegalArgumentException("waist must be greater than neck for Navy formula");
            }
            density = 1.0324
                    - 0.19077 * Math.log10(waistMinusNeck)
                    + 0.15456 * Math.log10(height);
        } else {
            double hip = circumferencesCm.get("hip").doubleValue();
            double waistPlusHipMinusNeck = waist + hip - neck;
            if (waistPlusHipMinusNeck <= 0) {
                throw new IllegalArgumentException("waist + hip must be greater than neck for Navy formula");
            }
            density = 1.29579
                    - 0.35004 * Math.log10(waistPlusHipMinusNeck)
                    + 0.22100 * Math.log10(height);
        }
        return siriClampScale(density);
    }

    // ── shared scaling ──────────────────────────────────────────────────

    private static BigDecimal siriClampScale(double bodyDensity) {
        // Siri: BF% = (495 / BD) - 450
        double pct = (495.0 / bodyDensity) - 450.0;
        return clampScale(pct);
    }

    private static BigDecimal clampScale(double pct) {
        double clamped = Math.max(0, Math.min(100, pct));
        return BigDecimal.valueOf(clamped).setScale(2, RoundingMode.HALF_UP);
    }

    // ── ergonomics helper ───────────────────────────────────────────────

    /** Tries to read an int from an Optional, returning empty when null. */
    static Optional<Integer> opt(Integer v) {
        return Optional.ofNullable(v);
    }
}
