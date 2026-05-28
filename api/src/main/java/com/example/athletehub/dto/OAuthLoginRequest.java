package com.example.athletehub.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body of POST /api/auth/oauth/{provider}: the ID token the mobile SDK obtained. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OAuthLoginRequest {

    @NotBlank
    private String idToken;
}
