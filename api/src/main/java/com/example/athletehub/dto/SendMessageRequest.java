package com.example.athletehub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body for {@code POST /api/conversations/{id}/messages}. {@code body} is
 * required and bounded — the schema-side CHECK accepts 1..4000 and Bean
 * Validation enforces the same range earlier so the caller gets a 400
 * with field errors instead of a 500-mapped IntegrityViolation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendMessageRequest {

    @NotBlank
    @Size(min = 1, max = 4000)
    private String body;
}
