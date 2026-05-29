package com.example.athletehub.controller;

import com.example.athletehub.dto.CreateExerciseRequest;
import com.example.athletehub.dto.CursorPage;
import com.example.athletehub.dto.ExerciseDto;
import com.example.athletehub.security.UserPrincipal;
import com.example.athletehub.service.ExerciseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exercise catalog endpoints (AH-031).
 *
 * <p>Path style note: the architecture spec mentions {@code /api/v1/exercises},
 * but every other endpoint in this codebase is unversioned ({@code /api/auth/*},
 * {@code /api/me/*}, {@code /api/users/*}) — we keep the convention consistent
 * here and will introduce {@code /v1} across the board when versioning lands.
 */
@RestController
@RequestMapping("/api/exercises")
@RequiredArgsConstructor
public class ExerciseController {

    private final ExerciseService exerciseService;

    @GetMapping
    public CursorPage<ExerciseDto> search(Authentication authentication,
                                          @RequestParam(value = "q", required = false) String q,
                                          @RequestParam(value = "cursor", required = false) Long cursor,
                                          @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return exerciseService.search(currentUserId(authentication), q, cursor, clampLimit(limit));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExerciseDto create(Authentication authentication,
                              @Valid @RequestBody CreateExerciseRequest request) {
        return exerciseService.createCustom(currentUserId(authentication), request);
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
