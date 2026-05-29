package com.example.athletehub.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body for {@code POST /api/workout-sessions}. Both fields optional —
 * if {@code templateId} is set, the new session inherits its name and
 * seeds {@code session_exercises} from the template; otherwise the
 * session is ad-hoc and starts empty. {@code title} overrides the
 * template's name when provided (useful for "Push A — deload week").
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StartSessionRequest {

    private Long templateId;

    @Size(max = 120)
    private String title;
}
