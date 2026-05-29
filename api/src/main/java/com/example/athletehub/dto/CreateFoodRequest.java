package com.example.athletehub.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Payload for {@code POST /api/foods}. Macros are given for
 * {@code servingSizeG} grams; the diet calculator scales linearly to
 * whatever amount the user logs. Bean validation mirrors the schema
 * CHECKs (serving > 0, macros ≥ 0) so a bad payload returns 400
 * VALIDATION_FAILED instead of 500 DataIntegrityViolation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateFoodRequest {

    @NotBlank
    @Size(min = 1, max = 120)
    private String name;

    @Size(max = 80)
    private String brand;

    @NotNull
    @DecimalMin(value = "0.01", message = "serving size must be > 0")
    private BigDecimal servingSizeG;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal kcal;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal proteinG;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal carbG;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal fatG;

    @DecimalMin("0.0")
    private BigDecimal fiberG;

    @DecimalMin("0.0")
    private BigDecimal sodiumMg;
}
