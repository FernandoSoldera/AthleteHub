package com.example.athletehub.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body for {@code PUT /api/me/coach-profile}. Both fields nullable —
 * sending one and not the other patches just the field that's present.
 * Ratings + athlete_count are server-maintained and not editable here.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCoachProfileRequest {

    @Size(max = 200)
    private String headline;

    @Min(0)
    @Max(80)
    private Integer yearsExperience;
}
