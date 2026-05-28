package com.example.athletehub.controller;

import com.example.athletehub.dto.AuthResponse;
import com.example.athletehub.dto.LoginRequest;
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
 * {@code SecurityConfig}. Token refresh (AH-013), password reset (AH-014) and
 * OAuth (AH-015) follow. Invalid credentials surface as
 * {@link com.example.athletehub.exception.InvalidCredentialsException} from the
 * service and are translated to a 401 JSON envelope by the global handler.
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
}
