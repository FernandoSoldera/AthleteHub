package com.example.athletehub.controller;

import com.example.athletehub.dto.CursorPage;
import com.example.athletehub.dto.MyCoachDto;
import com.example.athletehub.dto.RosterEntryDto;
import com.example.athletehub.security.UserPrincipal;
import com.example.athletehub.service.CoachLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AH-072 — coach roster + athlete-side "who is my coach?" view. Two routes:
 *
 * <ul>
 *   <li>{@code GET /api/coach/athletes?status=&flag=&cursor=&limit=} —
 *       coach's roster. Defaults to {@code status = "active"}; valid
 *       overrides are {@code pending} / {@code ended}. Optional flag
 *       filter (silently dropped if unknown).</li>
 *   <li>{@code GET /api/me/coach} — the caller's active coach
 *       relationship, hydrated. Returns null when none.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CoachRosterController {

    private final CoachLinkService coachLinkService;

    @GetMapping("/coach/athletes")
    public CursorPage<RosterEntryDto> roster(
            Authentication authentication,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "flag", required = false) String flag,
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return coachLinkService.listRoster(
                currentUserId(authentication), status, flag, cursor, clampLimit(limit));
    }

    @GetMapping("/me/coach")
    public MyCoachDto myCoach(Authentication authentication) {
        return coachLinkService.getMyCoach(currentUserId(authentication));
    }

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
