package com.example.athletehub.controller;

import com.example.athletehub.dto.AddFavoriteRequest;
import com.example.athletehub.dto.CreateDiaryEntryRequest;
import com.example.athletehub.dto.CursorPage;
import com.example.athletehub.dto.DayResponse;
import com.example.athletehub.dto.DiaryEntryDto;
import com.example.athletehub.dto.DietPlanDto;
import com.example.athletehub.dto.FavoriteDto;
import com.example.athletehub.dto.SetActivePlanRequest;
import com.example.athletehub.security.UserPrincipal;
import com.example.athletehub.service.DietService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Diet endpoints — active plan + day endpoint (AH-052), diary CRUD +
 * favorites (AH-053).
 *
 * <ul>
 *   <li>{@code GET /api/diet/active} — hydrated active plan or
 *       {@code null} when no plan is set.</li>
 *   <li>{@code POST /api/diet/active} — set or clear the active plan
 *       ({@code planId: null} clears).</li>
 *   <li>{@code GET /api/diet/day?date=YYYY-MM-DD} — day diary aggregate.
 *       Date is optional; defaults to today.</li>
 *   <li>{@code POST /api/diet/diary} — add a diary entry.</li>
 *   <li>{@code DELETE /api/diet/diary/{id}} — remove a diary entry.</li>
 *   <li>{@code GET /api/diet/favorites} — list favorites (cursor-paged).</li>
 *   <li>{@code POST /api/diet/favorites} — add or no-op-find a favorite.</li>
 *   <li>{@code DELETE /api/diet/favorites/{foodId}} — remove a favorite
 *       (idempotent).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/diet")
@RequiredArgsConstructor
public class DietController {

    private final DietService dietService;

    // ── active plan (AH-052) ──────────────────────────────────────────────

    @GetMapping("/active")
    public DietPlanDto getActive(Authentication authentication) {
        return dietService.getActivePlan(currentUserId(authentication));
    }

    @PostMapping("/active")
    public DietPlanDto setActive(Authentication authentication,
                                 @Valid @RequestBody(required = false) SetActivePlanRequest request) {
        return dietService.setActivePlan(currentUserId(authentication), request);
    }

    // ── day endpoint (AH-052) ─────────────────────────────────────────────

    @GetMapping("/day")
    public DayResponse getDay(Authentication authentication,
                              @RequestParam(value = "date", required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return dietService.getDay(currentUserId(authentication), date);
    }

    // ── diary entries (AH-053) ────────────────────────────────────────────

    @PostMapping("/diary")
    @ResponseStatus(HttpStatus.CREATED)
    public DiaryEntryDto addDiaryEntry(Authentication authentication,
                                       @Valid @RequestBody CreateDiaryEntryRequest request) {
        return dietService.addDiaryEntry(currentUserId(authentication), request);
    }

    @DeleteMapping("/diary/{id:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDiaryEntry(Authentication authentication,
                                 @PathVariable("id") Long id) {
        dietService.deleteDiaryEntry(currentUserId(authentication), id);
    }

    // ── favorites (AH-053) ────────────────────────────────────────────────

    @GetMapping("/favorites")
    public CursorPage<FavoriteDto> listFavorites(
            Authentication authentication,
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return dietService.listFavorites(currentUserId(authentication), cursor, clampLimit(limit));
    }

    @PostMapping("/favorites")
    @ResponseStatus(HttpStatus.CREATED)
    public FavoriteDto addFavorite(Authentication authentication,
                                   @Valid @RequestBody AddFavoriteRequest request) {
        return dietService.addFavorite(currentUserId(authentication), request);
    }

    @DeleteMapping("/favorites/{foodId:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFavorite(Authentication authentication,
                               @PathVariable("foodId") Long foodId) {
        dietService.removeFavorite(currentUserId(authentication), foodId);
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
