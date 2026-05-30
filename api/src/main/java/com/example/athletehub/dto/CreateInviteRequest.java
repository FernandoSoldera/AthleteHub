package com.example.athletehub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body for {@code POST /api/coach/invites}. The athlete is identified by
 * their public handle — same lookup as {@code GET /api/users/{handle}}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateInviteRequest {

    @NotBlank
    @Size(min = 3, max = 40)
    private String handle;
}
