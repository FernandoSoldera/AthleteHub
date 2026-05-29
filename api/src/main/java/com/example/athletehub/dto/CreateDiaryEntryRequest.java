package com.example.athletehub.dto;

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

/**
 * Body for {@code POST /api/diet/diary}.
 *
 * <p>{@code eatenAt} defaults to now() server-side when null so the
 * client only needs to set it for backfilled entries. {@code source}
 * defaults to {@code "self"}; the {@code @Pattern} accepts
 * {@code self | plan | favorite} — {@code coach} is reserved for the
 * Epic 7 assignments path and is rejected from the client API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateDiaryEntryRequest {

    @NotNull
    private Long foodId;

    @NotNull
    @DecimalMin(value = "0.01", message = "amount must be > 0")
    private BigDecimal amount;

    @NotNull
    @Pattern(regexp = "^(g|ml|portion)$",
             message = "unit must be 'g', 'ml' or 'portion'")
    private String unit;

    @Size(max = 60)
    private String mealLabel;

    private OffsetDateTime eatenAt;

    @Pattern(regexp = "^(self|plan|favorite)$",
             message = "source must be 'self', 'plan' or 'favorite'")
    private String source;
}
