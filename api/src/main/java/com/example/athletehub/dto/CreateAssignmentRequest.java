package com.example.athletehub.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Body for {@code POST /api/coach/athletes/{athleteId}/assignments}.
 *
 * <p>{@code refType} + {@code refId} are a soft link to the underlying
 * asset (template / plan / eval request) — the service enforces "both or
 * neither" before the schema CHECK fires. We don't validate that the
 * referenced row exists at create time: the soft link is allowed to go
 * stale gracefully (a coach deleting a template later doesn't blow away
 * historical assignments).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAssignmentRequest {

    @NotNull
    @Pattern(regexp = "^(workout|diet|eval)$",
             message = "type must be 'workout', 'diet' or 'eval'")
    private String type;

    @Pattern(regexp = "^(workout_template|diet_plan|eval_request)$",
             message = "refType must be 'workout_template', 'diet_plan' or 'eval_request'")
    private String refType;

    private Long refId;

    private LocalDate scheduledFor;

    @Size(max = 2000)
    private String notes;
}
