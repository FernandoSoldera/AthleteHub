package com.example.athletehub.dto;

/**
 * {@code GET /api/training/today} payload.
 *
 * <ul>
 *   <li>{@code template} = null → no plan scheduled for today (rest day).</li>
 *   <li>{@code activeSessionId} != null → the user already has an
 *       in-progress session; the client should offer "Resume" instead of
 *       "Start".</li>
 * </ul>
 *
 * <p>Both fields independently nullable because the four combinations are
 * all real states: no plan + no active (rest day), plan + no active (ready
 * to start), no plan + active (yesterday's session still open), plan +
 * active (today's planned, but you're already in one).
 */
public record TodayPlanResponse(
        WorkoutTemplateDto template,
        Long activeSessionId
) {}
