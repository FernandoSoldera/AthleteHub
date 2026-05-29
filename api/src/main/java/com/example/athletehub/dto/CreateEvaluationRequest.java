package com.example.athletehub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Body for {@code POST /api/evaluations}.
 *
 * <p>Three shapes are valid:
 * <ul>
 *   <li><b>Weight-only check-in</b> — {@code bfMethod} absent, {@code
 *       bodyFatPct} absent. Measurements may still be supplied (they're
 *       stored regardless of method so the time-series graphs always have
 *       data).</li>
 *   <li><b>Manual body-fat</b> — {@code bfMethod = "manual"} +
 *       {@code bodyFatPct} required. The service stamps both fields and
 *       skips formula computation.</li>
 *   <li><b>Computed body-fat</b> — {@code bfMethod ∈
 *       {jackson_pollock_7, navy}}. {@code bodyFatPct} is ignored if
 *       supplied; the service computes it from {@code measurements} +
 *       the user's {@code sex}, {@code age}, {@code heightCm} (per
 *       formula). Missing inputs → 400 with a specific message code.</li>
 * </ul>
 *
 * <p>{@code durnin} is reserved by the schema CHECK but not yet
 * implemented server-side — clients sending it get 400
 * {@code BF_METHOD_NOT_SUPPORTED} until AH-041 follow-up adds it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateEvaluationRequest {

    /** Defaults server-side to {@code now()} when null. */
    private OffsetDateTime evaluatedAt;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("999.99")
    private BigDecimal weightKg;

    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private BigDecimal bodyFatPct;

    /**
     * Optional. {@code manual} requires {@code bodyFatPct};
     * {@code jackson_pollock_7} requires 7 specific skinfolds + sex +
     * age; {@code navy} requires circumferences + sex + height.
     */
    @Pattern(regexp = "^(jackson_pollock_7|durnin|navy|manual)$",
             message = "bfMethod must be jackson_pollock_7, durnin, navy or manual")
    private String bfMethod;

    @Size(max = 2000)
    private String notes;

    @Valid
    private List<EvaluationMeasurementRequest> measurements;
}
