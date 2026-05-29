package com.example.athletehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body for {@code POST /api/diet/active}. A null {@code planId} clears
 * the active plan (returns user to "no plan / freestyle" mode).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetActivePlanRequest {
    private Long planId;
}
