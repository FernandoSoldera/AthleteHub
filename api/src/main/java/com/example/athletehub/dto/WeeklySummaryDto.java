package com.example.athletehub.dto;

import java.math.BigDecimal;

/**
 * This-week vs last-week cardio total. Distances in kilometres (BigDecimal
 * with 2 decimal places so the Train chart can label "12.3 km" without
 * client-side rounding).
 *
 * <p>{@code deltaKm = thisWeekKm - lastWeekKm} — a negative value means
 * "less than last week", which the chart renders as a downward delta chip.
 *
 * <p>Week boundaries are ISO weeks (Mon 00:00 … next Mon 00:00) in the
 * server's default time zone. For MVP this is good enough; a future
 * timezone-aware version reads the user's profile zone.
 */
public record WeeklySummaryDto(
        BigDecimal thisWeekKm,
        BigDecimal lastWeekKm,
        BigDecimal deltaKm
) {}
