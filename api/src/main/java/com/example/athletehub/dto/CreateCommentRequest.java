package com.example.athletehub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body for {@code POST /api/posts/{id}/comments}. The schema's
 * {@code LENGTH(body) > 0} CHECK guards a whitespace-only payload at the
 * data layer; the bean validation here gives a friendly 400 +
 * {@code VALIDATION_FAILED} instead of a 500.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCommentRequest {

    @NotBlank
    @Size(min = 1, max = 2000)
    private String body;
}
