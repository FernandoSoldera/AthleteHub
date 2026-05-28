package com.example.athletehub.controller;

import com.example.athletehub.dto.AuthResponse;
import com.example.athletehub.dto.ForgotPasswordRequest;
import com.example.athletehub.dto.LoginRequest;
import com.example.athletehub.dto.OAuthLoginRequest;
import com.example.athletehub.dto.RefreshTokenRequest;
import com.example.athletehub.dto.ResetPasswordRequest;
import com.example.athletehub.dto.SignupRequest;
import com.example.athletehub.dto.UserDto;
import com.example.athletehub.enums.OAuthProvider;
import com.example.athletehub.service.AuthService;
import com.example.athletehub.service.SocialAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * Public auth endpoints. The path prefix {@code /api/auth/**} is permitted by
 * {@code SecurityConfig}. OAuth (AH-015) follows. Domain errors surface via
 * the global advice as 401/400/409 JSON envelopes.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SocialAuthService socialAuthService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto register(@Valid @RequestBody SignupRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String deviceInfo = httpRequest.getHeader("User-Agent");
        return authService.login(request, deviceInfo);
    }

    @PostMapping("/token/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
        String deviceInfo = httpRequest.getHeader("User-Agent");
        return authService.refresh(request.getRefreshToken(), deviceInfo);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
    }

    /**
     * Start a password reset. Always returns 202 — we never reveal whether the
     * email exists (no account enumeration).
     */
    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
    }

    /** Consume a reset code and set a new password. Single-use. */
    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getCode(), request.getPassword());
    }

    /**
     * Exchange a provider-issued ID token for our own access + refresh tokens.
     * The mobile app handles the actual OAuth flow; we just verify the token
     * and link / create the user.
     */
    @PostMapping("/oauth/{provider}")
    public AuthResponse oauthLogin(@PathVariable("provider") String providerName,
                                   @Valid @RequestBody OAuthLoginRequest request,
                                   HttpServletRequest httpRequest) {
        OAuthProvider provider;
        try {
            provider = OAuthProvider.valueOf(providerName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new com.example.athletehub.exception.InvalidOAuthTokenException(
                    "Unsupported provider: " + providerName);
        }
        String deviceInfo = httpRequest.getHeader("User-Agent");
        return socialAuthService.loginWithProvider(provider, request.getIdToken(), deviceInfo);
    }
}
