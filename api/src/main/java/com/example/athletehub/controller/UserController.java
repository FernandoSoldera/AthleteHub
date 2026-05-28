package com.example.athletehub.controller;

import com.example.athletehub.dto.SwitchRoleRequest;
import com.example.athletehub.dto.UpdateProfileRequest;
import com.example.athletehub.dto.UserDto;
import com.example.athletehub.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated user-scoped endpoints. The JWT filter (AH-012) populates the
 * {@link Authentication} principal with the user's email; without that filter
 * Spring Security will return 401 before any method here is invoked.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserDto me(Authentication authentication) {
        return userService.getByEmail(authentication.getName());
    }

    @PatchMapping("/me")
    public UserDto updateMe(Authentication authentication,
                            @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(authentication.getName(), request);
    }

    /**
     * Switch into a role. If the user doesn't already hold the role, it's
     * granted (the design's "explicit upgrade" path). Returns the refreshed
     * profile so the client can re-render its UI for the new role.
     */
    @PostMapping("/me/roles/switch")
    public UserDto switchRole(Authentication authentication,
                              @Valid @RequestBody SwitchRoleRequest request) {
        return userService.switchRole(authentication.getName(), request.getRole());
    }
}
