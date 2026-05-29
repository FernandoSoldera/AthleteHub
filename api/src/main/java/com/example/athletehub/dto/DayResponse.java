package com.example.athletehub.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /api/diet/day?date=YYYY-MM-DD} payload.
 *
 * <ul>
 *   <li>{@code totals} = sum of scaled macros across {@code entries}.</li>
 *   <li>{@code target} = active plan's daily target, or null when no
 *       plan is active.</li>
 *   <li>{@code remaining} = {@code target − totals}, per macro; null
 *       when {@code target} is null. Values can go negative when the
 *       user has gone over (the chart will show "1200 kcal over").</li>
 * </ul>
 */
public record DayResponse(
        LocalDate date,
        List<DiaryEntryDto> entries,
        Macros totals,
        Macros target,
        Macros remaining
) {}
