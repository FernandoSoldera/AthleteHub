package com.example.athletehub.controller;

import com.example.athletehub.dto.CreateEvaluationRequest;
import com.example.athletehub.dto.CursorPage;
import com.example.athletehub.dto.EvaluationDto;
import com.example.athletehub.dto.EvaluationSummaryDto;
import com.example.athletehub.security.UserPrincipal;
import com.example.athletehub.service.EvaluationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * AH-041 evaluation endpoints. Unversioned to match the rest of the API
 * (see the AH-031 path-versioning note in the backlog).
 */
@RestController
@RequestMapping("/api/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EvaluationDto create(Authentication authentication,
                                @Valid @RequestBody CreateEvaluationRequest request) {
        return evaluationService.create(currentUserId(authentication), request);
    }

    @GetMapping
    public CursorPage<EvaluationSummaryDto> listRecent(
            Authentication authentication,
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return evaluationService.listRecent(currentUserId(authentication), cursor, clampLimit(limit));
    }

    @GetMapping("/{id:\\d+}")
    public EvaluationDto getById(Authentication authentication,
                                 @PathVariable("id") Long id) {
        return evaluationService.getById(currentUserId(authentication), id);
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
