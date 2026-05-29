package com.example.athletehub.controller;

import com.example.athletehub.dto.MetricSeriesDto;
import com.example.athletehub.security.UserPrincipal;
import com.example.athletehub.service.EvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Body / Evolution charts (AH-042). Separate from
 * {@code EvaluationController} because the series view is a computed
 * derivation across evaluations + measurements rather than CRUD on a
 * single evaluation row.
 */
@RestController
@RequestMapping("/api/body")
@RequiredArgsConstructor
public class BodyController {

    private final EvaluationService evaluationService;

    @GetMapping("/series")
    public MetricSeriesDto series(Authentication authentication,
                                  @RequestParam("metric") String metric,
                                  @RequestParam("range") String range) {
        return evaluationService.getMetricSeries(currentUserId(authentication), metric, range);
    }

    private Long currentUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal up) return up.getId();
        throw new IllegalStateException("Unexpected principal: " + principal.getClass());
    }
}
