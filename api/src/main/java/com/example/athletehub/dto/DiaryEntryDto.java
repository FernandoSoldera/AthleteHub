package com.example.athletehub.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One diary entry as seen by the client. Carries the food + the scaled
 * macros so the day strip can render without extra round-trips.
 */
public record DiaryEntryDto(
        Long id,
        Long foodId,
        String foodName,
        OffsetDateTime eatenAt,
        BigDecimal amount,
        String unit,
        String mealLabel,
        String source,
        Macros macros
) {}
