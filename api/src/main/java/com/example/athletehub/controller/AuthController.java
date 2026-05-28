package com.example.athletehub.controller;

import com.example.athletehub.dto.AuthResponse;
import com.example.athletehub.dto.LoginRequest;
import com.example.athletehub.dto.RefreshTokenRequest;
import com.example.athletehub.dto.SignupRequest;
import com.example.athletehub.dto.UserDto;
import com.example.athletehub.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public auth endpoints. The path prefix {@code /api/auth/**} is permitted by
 * {@code SecurityConfig}. Password reset (AH-014) and OAuth (AH-015) follow.
 * Invalid credentials / invalid refresh tokens surface as domain exceptions
 * translated to 401 JSON envelopes by the global handler.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

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
}
