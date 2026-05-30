package com.example.athletehub.controller;

import com.example.athletehub.dto.CoachProfileDto;
import com.example.athletehub.dto.CursorPage;
import com.example.athletehub.dto.MyCoachDto;
import com.example.athletehub.dto.RosterEntryDto;
import com.example.athletehub.dto.StudentDetailDto;
import com.example.athletehub.dto.UpdateCoachProfileRequest;
import com.example.athletehub.security.UserPrincipal;
import com.example.athletehub.service.CoachLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AH-072 / AH-073 / AH-075 coach surfaces. Five routes:
 *
 * <ul>
 *   <li>{@code GET /api/coach/athletes?status=&flag=&cursor=&limit=} —
 *       coach's roster (AH-072).</li>
 *   <li>{@code GET /api/coach/athletes/{id:\\d+}} — student detail
 *       (AH-073).</li>
 *   <li>{@code GET /api/me/coach} — athlete-side "who is my coach?"
 *       view (AH-072).</li>
 *   <li>{@code GET /api/me/coach-profile} — caller's coach card
 *       (lazy default for first read; AH-075).</li>
 *   <li>{@code PUT /api/me/coach-profile} — upsert headline + years
 *       experience (AH-075).</li>
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

    @GetMapping("/coach/athletes/{id:\\d+}")
    public StudentDetailDto studentDetail(Authentication authentication,
                                          @PathVariable("id") Long id) {
        return coachLinkService.getStudentDetail(currentUserId(authentication), id);
    }

    @GetMapping("/me/coach-profile")
    public CoachProfileDto myCoachProfile(Authentication authentication) {
        return coachLinkService.getMyCoachProfile(currentUserId(authentication));
    }

    @PutMapping("/me/coach-profile")
    public CoachProfileDto upsertMyCoachProfile(Authentication authentication,
                                                @Valid @RequestBody UpdateCoachProfileRequest request) {
        return coachLinkService.upsertMyCoachProfile(currentUserId(authentication), request);
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
