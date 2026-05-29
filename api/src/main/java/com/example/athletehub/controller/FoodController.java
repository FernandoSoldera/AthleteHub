package com.example.athletehub.controller;

import com.example.athletehub.dto.CreateFoodRequest;
import com.example.athletehub.dto.CursorPage;
import com.example.athletehub.dto.FoodDto;
import com.example.athletehub.security.UserPrincipal;
import com.example.athletehub.service.FoodService;
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
 * Food catalog endpoints (AH-051). Unversioned to match the rest of the
 * API (see the AH-031 path-versioning note in the backlog).
 */
@RestController
@RequestMapping("/api/foods")
@RequiredArgsConstructor
public class FoodController {

    private final FoodService foodService;

    @GetMapping
    public CursorPage<FoodDto> search(Authentication authentication,
                                      @RequestParam(value = "q", required = false) String q,
                                      @RequestParam(value = "cursor", required = false) Long cursor,
                                      @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return foodService.search(currentUserId(authentication), q, cursor, clampLimit(limit));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FoodDto create(Authentication authentication,
                          @Valid @RequestBody CreateFoodRequest request) {
        return foodService.createCustom(currentUserId(authentication), request);
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
