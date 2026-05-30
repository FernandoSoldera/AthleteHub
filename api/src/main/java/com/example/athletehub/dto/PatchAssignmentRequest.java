package com.example.athletehub.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Body for {@code PATCH /api/coach/assignments/{id}}. Every field is
 * optional — only those present in the payload are applied. {@code
 * refType} + {@code refId} are not modifiable here (rebuild the
 * reference by creating a new assignment).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatchAssignmentRequest {

    @Pattern(regexp = "^(scheduled|today|pending|done|skipped)$",
             message = "status must be one of scheduled, today, pending, done, skipped")
    private String status;

    private LocalDate scheduledFor;

    @Size(max = 2000)
    private String notes;
}
