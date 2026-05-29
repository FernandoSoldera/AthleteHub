package com.example.athletehub.controller;

import com.example.athletehub.dto.DayResponse;
import com.example.athletehub.dto.DietPlanDto;
import com.example.athletehub.dto.SetActivePlanRequest;
import com.example.athletehub.security.UserPrincipal;
import com.example.athletehub.service.DietService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Diet endpoints (AH-052). Three routes:
 *
 * <ul>
 *   <li>{@code GET /api/diet/active} — hydrated active plan or
 *       {@code null} when no plan is set.</li>
 *   <li>{@code POST /api/diet/active} — set or clear the active
 *       plan ({@code planId: null} clears).</li>
 *   <li>{@code GET /api/diet/day?date=YYYY-MM-DD} — day diary
 *       aggregate. Date is optional; defaults to today.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/diet")
@RequiredArgsConstructor
public class DietController {

    private final DietService dietService;

    @GetMapping("/active")
    public DietPlanDto getActive(Authentication authentication) {
        return dietService.getActivePlan(currentUserId(authentication));
    }

    @PostMapping("/active")
    public DietPlanDto setActive(Authentication authentication,
                                 @Valid @RequestBody(required = false) SetActivePlanRequest request) {
        return dietService.setActivePlan(currentUserId(authentication), request);
    }

    @GetMapping("/day")
    public DayResponse getDay(Authentication authentication,
                              @RequestParam(value = "date", required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return dietService.getDay(currentUserId(authentication), date);
    }

    private Long currentUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal up) return up.getId();
        throw new IllegalStateException("Unexpected principal: " + principal.getClass());
    }
}
