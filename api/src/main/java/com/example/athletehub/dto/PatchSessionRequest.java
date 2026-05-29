package com.example.athletehub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Body for {@code PATCH /api/workout-sessions/{id}}. A batch of granular
 * set operations to apply atomically — either every op succeeds or none do.
 * The list is small in practice (one or two ops per "tap done") so we cap
 * it modestly to keep the surface predictable.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatchSessionRequest {

    @NotNull
    @Size(min = 1, max = 100)
    @Valid
    private List<SetOpRequest> sets;
}
