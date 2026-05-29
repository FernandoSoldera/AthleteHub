package com.example.athletehub.controller;

import com.example.athletehub.dto.CursorPage;
import com.example.athletehub.dto.PublicUserDto;
import com.example.athletehub.dto.SwitchRoleRequest;
import com.example.athletehub.dto.UpdateProfileRequest;
import com.example.athletehub.dto.UserDto;
import com.example.athletehub.security.UserPrincipal;
import com.example.athletehub.service.FollowService;
import com.example.athletehub.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated user-scoped endpoints. The JWT filter populates the
 * {@link Authentication} principal with a {@link UserPrincipal}; without that
 * filter, Spring Security returns 401 before any method here is invoked.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final FollowService followService;

    // ── /me ────────────────────────────────────────────────────────────────

    @GetMapping("/me")
    public UserDto me(Authentication authentication) {
        return userService.getByEmail(authentication.getName());
    }

    @PatchMapping("/me")
    public UserDto updateMe(Authentication authentication,
                            @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(authentication.getName(), request);
    }

    @PostMapping("/me/roles/switch")
    public UserDto switchRole(Authentication authentication,
                              @Valid @RequestBody SwitchRoleRequest request) {
        return userService.switchRole(authentication.getName(), request.getRole());
    }

    // ── follow graph ───────────────────────────────────────────────────────

    @PostMapping("/users/{id}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void followUser(Authentication authentication, @PathVariable("id") Long id) {
        followService.follow(currentUserId(authentication), id);
    }

    @DeleteMapping("/users/{id}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollowUser(Authentication authentication, @PathVariable("id") Long id) {
        followService.unfollow(currentUserId(authentication), id);
    }

    @GetMapping("/me/followers")
    public CursorPage<PublicUserDto> myFollowers(Authentication authentication,
                                                 @RequestParam(value = "cursor", required = false) Long cursor,
                                                 @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return followService.listFollowers(currentUserId(authentication), cursor, clampLimit(limit));
    }

    @GetMapping("/me/following")
    public CursorPage<PublicUserDto> myFollowing(Authentication authentication,
                                                 @RequestParam(value = "cursor", required = false) Long cursor,
                                                 @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return followService.listFollowing(currentUserId(authentication), cursor, clampLimit(limit));
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private Long currentUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal up) return up.getId();
        throw new IllegalStateException("Unexpected principal: " + principal.getClass());
    }

    private int clampLimit(int requested) {
        if (requested < 1) return 1;
        if (requested > 100) return 100;
        return requested;
    }
}
