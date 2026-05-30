package com.example.athletehub.service;

import com.example.athletehub.dto.AssignmentDto;
import com.example.athletehub.dto.CreateAssignmentRequest;
import com.example.athletehub.dto.CursorPage;
import com.example.athletehub.dto.PatchAssignmentRequest;
import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.exception.BadRequestException;
import com.example.athletehub.exception.ResourceNotFoundException;
import com.example.athletehub.model.Assignment;
import com.example.athletehub.model.CoachAthlete;
import com.example.athletehub.repository.AssignmentRepository;
import com.example.athletehub.repository.CoachAthleteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * AH-074 — assign workout / diet / eval to an athlete + manage the
 * resulting assignment rows.
 *
 * <h2>Visibility</h2>
 *
 * Every coach-side mutation runs through the active-relationship gate:
 * the caller must be the coach on an {@code active} {@code coach_athlete}
 * row that owns the assignment. "Not yours" / "doesn't exist" both
 * return 404 {@code ASSIGNMENT_NOT_FOUND} so the API doesn't leak
 * existence by timing.
 *
 * <p>The athlete side gets a read-only listing of their own assignments
 * (across all their active relationships — typically one at MVP). Future
 * stories will let the athlete flip their own assignments to
 * {@code done}/{@code skipped} once the start-from-assignment flow lands.
 */
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final CoachAthleteRepository coachAthleteRepository;

    // ── create (coach) ────────────────────────────────────────────────────

    @Transactional
    public AssignmentDto create(Long coachId, Long athleteId, CreateAssignmentRequest request) {
        CoachAthlete rel = activeRelationshipOrThrow(coachId, athleteId);

        // ref pair XOR: pre-validate so we return a friendly 400 before the
        // schema CHECK would otherwise turn this into 500.
        if ((request.getRefType() == null) != (request.getRefId() == null)) {
            throw new BadRequestException(MessageCode.VALIDATION_FAILED);
        }

        Assignment saved = assignmentRepository.save(Assignment.builder()
                .coachAthleteId(rel.getId())
                .type(request.getType())
                .refType(request.getRefType())
                .refId(request.getRefId())
                .scheduledFor(request.getScheduledFor())
                .notes(trimToNull(request.getNotes()))
                .status("scheduled")
                .build());
        return AssignmentDto.from(saved);
    }

    // ── update (coach) ────────────────────────────────────────────────────

    @Transactional
    public AssignmentDto update(Long coachId, Long assignmentId, PatchAssignmentRequest request) {
        Assignment a = loadOwnedByCoach(coachId, assignmentId);

        // Partial update — only fields present in the payload are touched.
        if (request.getStatus() != null) a.setStatus(request.getStatus());
        if (request.getScheduledFor() != null) a.setScheduledFor(request.getScheduledFor());
        if (request.getNotes() != null) a.setNotes(trimToNull(request.getNotes()));

        return AssignmentDto.from(assignmentRepository.save(a));
    }

    // ── delete (coach) ────────────────────────────────────────────────────

    @Transactional
    public void delete(Long coachId, Long assignmentId) {
        Assignment a = loadOwnedByCoach(coachId, assignmentId);
        assignmentRepository.delete(a);
    }

    // ── list for athlete (coach side) ────────────────────────────────────

    @Transactional(readOnly = true)
    public CursorPage<AssignmentDto> listForAthlete(Long coachId, Long athleteId,
                                                    String status, LocalDate scheduledOn,
                                                    Long cursor, int limit) {
        CoachAthlete rel = activeRelationshipOrThrow(coachId, athleteId);
        // Split queries because PG can't infer the type of a null LocalDate
        // through JPQL's :param IS NULL pattern — see the repository doc.
        List<Assignment> rows = (scheduledOn == null)
                ? assignmentRepository.findForRelationship(
                        rel.getId(), status, cursor, PageRequest.of(0, limit + 1))
                : assignmentRepository.findForRelationshipOnDate(
                        rel.getId(), status, scheduledOn, cursor, PageRequest.of(0, limit + 1));
        return page(rows, limit);
    }

    // ── list mine (athlete side) ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public CursorPage<AssignmentDto> listMine(Long athleteId,
                                              String status, LocalDate scheduledOn,
                                              Long cursor, int limit) {
        // MVP: one active coach per athlete. We list across all active
        // relationships so the shape stays right if that ever relaxes.
        List<Long> relationshipIds = coachAthleteRepository
                .findByAthleteIdAndStatusOrderByIdDesc(athleteId, "active")
                .stream()
                .map(CoachAthlete::getId)
                .toList();
        if (relationshipIds.isEmpty()) return CursorPage.of(List.of(), null);

        // Same query split as listForAthlete — see the repository doc.
        List<Assignment> rows = (scheduledOn == null)
                ? assignmentRepository.findForRelationships(
                        relationshipIds, status, cursor, PageRequest.of(0, limit + 1))
                : assignmentRepository.findForRelationshipsOnDate(
                        relationshipIds, status, scheduledOn, cursor,
                        PageRequest.of(0, limit + 1));
        return page(rows, limit);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    /**
     * Loads the (coach, athlete) relationship and rejects if it isn't
     * active or the caller isn't the coach. {@code RESOURCE_NOT_FOUND}
     * keeps the path "not yours" / "doesn't exist" indistinguishable.
     */
    private CoachAthlete activeRelationshipOrThrow(Long coachId, Long athleteId) {
        return coachAthleteRepository.findByCoachIdAndAthleteId(coachId, athleteId)
                .filter(r -> "active".equals(r.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.RESOURCE_NOT_FOUND));
    }

    /**
     * Loads an assignment that the caller (a coach) is allowed to mutate.
     * Walks the assignment → relationship → coach chain in one method so
     * the ownership rule lives in one place.
     */
    private Assignment loadOwnedByCoach(Long coachId, Long assignmentId) {
        Assignment a = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.ASSIGNMENT_NOT_FOUND));
        CoachAthlete rel = coachAthleteRepository.findById(a.getCoachAthleteId()).orElse(null);
        if (rel == null || !rel.getCoachId().equals(coachId) || !"active".equals(rel.getStatus())) {
            throw new ResourceNotFoundException(MessageCode.ASSIGNMENT_NOT_FOUND);
        }
        return a;
    }

    private static CursorPage<AssignmentDto> page(List<Assignment> rows, int limit) {
        if (rows.isEmpty()) return CursorPage.of(List.of(), null);
        boolean hasMore = rows.size() > limit;
        List<Assignment> visible = hasMore ? rows.subList(0, limit) : rows;
        List<AssignmentDto> items = new ArrayList<>(visible.size());
        for (Assignment a : visible) items.add(AssignmentDto.from(a));
        String nextCursor = hasMore
                ? String.valueOf(visible.get(visible.size() - 1).getId())
                : null;
        return CursorPage.of(items, nextCursor);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
