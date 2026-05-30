package com.example.athletehub.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body for {@code POST /api/posts}. Manual posts always land with
 * {@code type = "manual"} — auto-posts come from the workout / cardio /
 * evaluation services internally and never through this endpoint.
 *
 * <p>At least one of {@code title} / {@code note} should be provided in
 * practice (the client should enforce that), but we don't reject an
 * empty post at the API layer — a future "share to feed" flow might
 * carry only an image (Epic 9), and we don't want to block it now.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateManualPostRequest {

    @Size(max = 200)
    private String title;

    @Size(max = 2000)
    private String note;

    @Pattern(regexp = "^(public|followers|private)$",
             message = "visibility must be 'public', 'followers' or 'private'")
    private String visibility;
}
