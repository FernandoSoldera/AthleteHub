package com.example.athletehub.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One granular edit inside a {@link PatchSessionRequest}. Each op is
 * idempotent on the natural key {@code (sessionExerciseId, setNumber)}:
 *
 * <ul>
 *   <li>{@code upsert} — insert if no row matches, update if one does.
 *       Carries the new weight / reps / rpe / done state.</li>
 *   <li>{@code delete} — drop the matching row if it exists; no-op if
 *       it doesn't (so retries are safe).</li>
 * </ul>
 *
 * <p>Choosing op semantics over diff-replace means a flaky network during
 * a workout costs at most the in-flight set, not the whole session.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetOpRequest {

    /** {@code "upsert"} or {@code "delete"}. */
    @NotNull
    private String op;

    @NotNull
    private Long sessionExerciseId;

    @NotNull
    @Min(1)
    private Integer setNumber;

    @DecimalMin("0.0")
    private BigDecimal weightKg;

    @Min(0)
    private Integer reps;

    @DecimalMin("0.0")
    @DecimalMax("10.0")
    private BigDecimal rpe;

    private Boolean done;
}
