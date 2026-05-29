package com.example.athletehub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for {@code POST /api/exercises}. Only {@code name} is required —
 * the user might not have a meaningful category/equipment for a one-off
 * lift. The category / muscle / equipment fields are kept free-form
 * strings rather than enums so users can write whatever fits their gym
 * ("trap bar", "kettlebell", "landmine") without us shipping a migration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateExerciseRequest {

    @NotBlank
    @Size(min = 1, max = 80)
    private String name;

    @Size(max = 30)
    private String category;

    @Size(max = 30)
    private String primaryMuscle;

    @Size(max = 30)
    private String equipment;
}
