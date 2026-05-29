package com.example.athletehub.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Body for {@code POST /api/cardio-activities}.
 *
 * <p>Bean validation mirrors the schema CHECKs so a bad payload returns
 * 400 with field errors instead of a 500 from {@code DataIntegrityViolation}.
 * Optional fields stay null when the source can't supply them (a manual
 * "I went for a run" log won't have HR or power).
 *
 * <p>{@code startedAt} defaults server-side to {@code now()} when null —
 * the client only needs to set it when backfilling an older activity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCardioRequest {

    @NotNull
    @Pattern(regexp = "run|walk|cycle", message = "must be run, walk or cycle")
    private String type;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal distanceM;

    @NotNull
    @PositiveOrZero
    private Integer durationSeconds;

    @DecimalMin("0.0")
    private BigDecimal avgPaceSPerKm;

    @DecimalMin("0.0")
    private BigDecimal avgPowerW;

    @Min(1)
    @Max(299)
    private Integer avgHr;

    @Min(1)
    @Max(299)
    private Integer maxHr;

    @DecimalMin("0.0")
    private BigDecimal elevationGainM;

    @PositiveOrZero
    private Integer kcal;

    @Size(max = 2000)
    private String notes;

    private OffsetDateTime startedAt;
}
