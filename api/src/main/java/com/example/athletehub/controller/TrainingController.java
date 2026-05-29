package com.example.athletehub.controller;

import com.example.athletehub.dto.StartSessionRequest;
import com.example.athletehub.dto.TodayPlanResponse;
import com.example.athletehub.dto.WorkoutSessionDto;
import com.example.athletehub.security.UserPrincipal;
import com.example.athletehub.service.TrainingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Training-day endpoints (AH-032).
 *
 * <ul>
 *   <li>{@code GET /api/training/today} — the hero card payload: today's
 *       planned template (if any) plus any in-progress session id so the
 *       client can render Start / Resume / Rest day deterministically.</li>
 *   <li>{@code POST /api/workout-sessions} — starts an {@code in_progress}
 *       session, optionally seeded from a template.</li>
 * </ul>
 *
 * <p>Path style: unversioned, like the rest of the API
 * (see the AH-031 note in the backlog).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class TrainingController {

    private final TrainingService trainingService;

    @GetMapping("/training/today")
    public TodayPlanResponse today(Authentication authentication) {
        return trainingService.getTodayPlan(currentUserId(authentication));
    }

    @PostMapping("/workout-sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutSessionDto startSession(Authentication authentication,
                                          @Valid @RequestBody(required = false) StartSessionRequest request) {
        return trainingService.startSession(currentUserId(authentication), request);
    }

    private Long currentUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal up) return up.getId();
        throw new IllegalStateException("Unexpected principal: " + principal.getClass());
    }
}
