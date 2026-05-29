package com.example.athletehub.dto;

import java.math.BigDecimal;

/**
 * Macro bundle — used as the totals, target, and remaining values on the
 * day endpoint and the per-entry payload. Fiber and sodium stay null when
 * the underlying food rows don't carry them, so the client can render a
 * dash rather than a misleading zero.
 */
public record Macros(
        BigDecimal kcal,
        BigDecimal proteinG,
        BigDecimal carbG,
        BigDecimal fatG,
        BigDecimal fiberG,
        BigDecimal sodiumMg
) {
    public static Macros zero() {
        return new Macros(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
