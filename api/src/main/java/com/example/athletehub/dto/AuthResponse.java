package com.example.athletehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Payload returned by successful login (and later by the refresh endpoint). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    /** Access token lifetime in ms — the client uses this to schedule refresh before expiry. */
    private Long accessTokenExpiresIn;
    @Builder.Default
    private String tokenType = "Bearer";
    private UserDto user;
}
