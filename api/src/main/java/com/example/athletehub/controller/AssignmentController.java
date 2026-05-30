package com.example.athletehub.controller;

import com.example.athletehub.dto.AssignmentDto;
import com.example.athletehub.dto.CreateAssignmentRequest;
import com.example.athletehub.dto.CursorPage;
import com.example.athletehub.dto.PatchAssignmentRequest;
import com.example.athletehub.security.UserPrincipal;
import com.example.athletehub.service.AssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * AH-074 — assign workout / diet / eval. Five routes:
 *
 * <ul>
 *   <li>{@code POST /api/coach/athletes/{id}/assignments} — coach creates
 *       a new assignment for one athlete.</li>
 *   <li>{@code GET /api/coach/athletes/{id}/assignments?status=&scheduledOn=&cursor=&limit=}
 *       — coach lists assignments for one athlete.</li>
 *   <li>{@code PATCH /api/coach/assignments/{id}} — coach updates status
 *       / scheduledFor / notes.</li>
 *   <li>{@code DELETE /api/coach/assignments/{id}} — coach removes (hard
 *       delete; the SET-NULL FKs on workout_sessions / cardio_activities
 *       handle the historical references).</li>
 *   <li>{@code GET /api/me/assignments?status=&scheduledOn=&cursor=&limit=}
 *       — athlete lists their own assignments across all active
 *       relationships.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    // ── coach-side ────────────────────────────────────────────────────────

    @PostMapping("/coach/athletes/{athleteId:\\d+}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentDto create(Authentication authentication,
                                @PathVariable("athleteId") Long athleteId,
                                @Valid @RequestBody CreateAssignmentRequest request) {
        return assignmentService.create(currentUserId(authentication), athleteId, request);
    }

    @GetMapping("/coach/athletes/{athleteId:\\d+}/assignments")
    public CursorPage<AssignmentDto> listForAthlete(
            Authentication authentication,
            @PathVariable("athleteId") Long athleteId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "scheduledOn", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduledOn,
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return assignmentService.listForAthlete(
                currentUserId(authentication), athleteId, status, scheduledOn,
                cursor, clampLimit(limit));
    }

    @PatchMapping("/coach/assignments/{id:\\d+}")
    public AssignmentDto update(Authentication authentication,
                                @PathVariable("id") Long id,
                                @Valid @RequestBody PatchAssignmentRequest request) {
        return assignmentService.update(currentUserId(authentication), id, request);
    }

    @DeleteMapping("/coach/assignments/{id:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication,
                       @PathVariable("id") Long id) {
        assignmentService.delete(currentUserId(authentication), id);
    }

    // ── athlete-side ──────────────────────────────────────────────────────

    @GetMapping("/me/assignments")
    public CursorPage<AssignmentDto> listMine(
            Authentication authentication,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "scheduledOn", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduledOn,
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return assignmentService.listMine(
                currentUserId(authentication), status, scheduledOn,
                cursor, clampLimit(limit));
    }

    // ── helpers ───────────────────────────────────────────────────────────

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
