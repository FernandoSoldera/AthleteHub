package com.example.athletehub.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One measurement inside a {@link CreateEvaluationRequest}. Bean
 * validation mirrors the schema CHECKs (kind ∈ {circumference, skinfold},
 * unit ∈ {cm, mm}, value ≥ 0) so a bad payload is 400 VALIDATION_FAILED,
 * not 500 DataIntegrityViolation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationMeasurementRequest {

    @NotBlank
    @Size(max = 50)
    private String pointId;

    @NotNull
    @Pattern(regexp = "^(circumference|skinfold)$",
             message = "kind must be 'circumference' or 'skinfold'")
    private String kind;

    @NotNull
    @Pattern(regexp = "^(cm|mm)$",
             message = "unit must be 'cm' or 'mm'")
    private String unit;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal value;
}
