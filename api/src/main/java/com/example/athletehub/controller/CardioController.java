package com.example.athletehub.controller;

import com.example.athletehub.dto.CardioActivityDto;
import com.example.athletehub.dto.CreateCardioRequest;
import com.example.athletehub.dto.CursorPage;
import com.example.athletehub.security.UserPrincipal;
import com.example.athletehub.service.CardioService;
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
 * Cardio endpoints (AH-034). Separate from {@code TrainingController}
 * because cardio is its own resource family — the recent-cardio strip
 * on the Train screen reads here independently of "today's plan".
 */
@RestController
@RequestMapping("/api/cardio-activities")
@RequiredArgsConstructor
public class CardioController {

    private final CardioService cardioService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CardioActivityDto create(Authentication authentication,
                                    @Valid @RequestBody CreateCardioRequest request) {
        return cardioService.create(currentUserId(authentication), request);
    }

    @GetMapping
    public CursorPage<CardioActivityDto> listRecent(Authentication authentication,
                                                    @RequestParam(value = "cursor", required = false) Long cursor,
                                                    @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return cardioService.listRecent(currentUserId(authentication), cursor, clampLimit(limit));
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
