package com.example.athletehub.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Partial update for the authenticated user's profile. All fields are
 * optional — only the ones present in the payload are applied.
 *
 * <p>{@link #handle} and {@link #email} are intentionally excluded: changing
 * them is a separate flow (uniqueness + reverification), not a profile edit.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {

    @Size(min = 1, max = 80)
    private String fullName;

    @Size(max = 280)
    private String bio;

    @Min(0)
    @Max(129)
    private Integer age;

    @jakarta.validation.constraints.DecimalMin("30.0")
    @jakarta.validation.constraints.DecimalMax("299.9")
    private BigDecimal heightCm;

    @Min(0)
    @Max(359)
    private Integer avatarHue;

    /**
     * Biological sex update — used by AH-041 body-fat formulas. Pattern
     * mirrors the schema CHECK so a bad value returns 400 not 500.
     */
    @Pattern(regexp = "^(male|female)$",
             message = "sex must be 'male' or 'female'")
    private String sex;
}
