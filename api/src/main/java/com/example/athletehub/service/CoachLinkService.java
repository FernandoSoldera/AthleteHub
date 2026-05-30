package com.example.athletehub.service;

import com.example.athletehub.dto.CoachInviteDto;
import com.example.athletehub.dto.CoachProfileDto;
import com.example.athletehub.dto.CreateInviteRequest;
import com.example.athletehub.dto.CursorPage;
import com.example.athletehub.dto.EvaluationSummaryDto;
import com.example.athletehub.dto.MyCoachDto;
import com.example.athletehub.dto.PublicUserDto;
import com.example.athletehub.dto.RosterEntryDto;
import com.example.athletehub.dto.StudentDetailDto;
import com.example.athletehub.dto.UpdateCoachProfileRequest;
import com.example.athletehub.dto.WeeklySummaryDto;
import com.example.athletehub.dto.WorkoutSessionSummaryDto;
import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.exception.BadRequestException;
import com.example.athletehub.exception.ConflictException;
import com.example.athletehub.exception.ResourceNotFoundException;
import com.example.athletehub.model.CoachAthlete;
import com.example.athletehub.model.CoachProfile;
import com.example.athletehub.model.User;
import com.example.athletehub.repository.CoachAthleteRepository;
import com.example.athletehub.repository.CoachProfileRepository;
import com.example.athletehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * AH-071 — coach↔athlete invite + consent linking. Four flows:
 *
 * <ul>
 *   <li><b>invite</b> — coach invites an athlete by handle. Creates a
 *       new {@code pending} row, or revives an {@code ended} one. An
 *       already-{@code pending} or {@code active} row → 409
 *       {@code COACH_LINK_EXISTS}.</li>
 *   <li><b>list incoming</b> — athlete's {@code pending} inbox.</li>
 *   <li><b>accept</b> — pending → active, stamps {@code since = today}.</li>
 *   <li><b>decline</b> — pending → ended.</li>
 * </ul>
 *
 * <p>Self-invite is blocked at the schema level (the
 * {@code coach_id <> athlete_id} CHECK) but we short-circuit here with a
 * friendlier 400 before the DB gets involved.
 */
@Service
@RequiredArgsConstructor
public class CoachLinkService {

    private final CoachAthleteRepository coachAthleteRepository;
    private final CoachProfileRepository coachProfileRepository;
    private final UserRepository userRepository;
    private final TrainingService trainingService;
    private final EvaluationService evaluationService;
    private final Clock clock;

    // ── invite ────────────────────────────────────────────────────────────

    @Transactional
    public CoachInviteDto invite(Long coachId, CreateInviteRequest request) {
        String handle = request.getHandle().trim().toLowerCase(Locale.ROOT);
        User athlete = userRepository.findByHandle(handle)
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.RESOURCE_NOT_FOUND));
        if (athlete.getId().equals(coachId)) {
            throw new BadRequestException("Cannot coach yourself");
        }

        // Find-or-revive: an `ended` row flips back to pending; pending/active blocks.
        CoachAthlete row = coachAthleteRepository
                .findByCoachIdAndAthleteId(coachId, athlete.getId())
                .map(existing -> {
                    switch (existing.getStatus()) {
                        case "pending", "active" ->
                                throw new ConflictException(MessageCode.COACH_LINK_EXISTS);
                        case "ended" -> {
                            existing.setStatus("pending");
                            existing.setSince(null);
                            existing.setFlag(null);
                            existing.setAdherencePct(null);
                        }
                        default -> throw new IllegalStateException(
                                "Unexpected coach_athlete status: " + existing.getStatus());
                    }
                    return existing;
                })
                .orElseGet(() -> CoachAthlete.builder()
                        .coachId(coachId)
                        .athleteId(athlete.getId())
                        .status("pending")
                        .build());

        CoachAthlete saved = coachAthleteRepository.save(row);
        User coach = userRepository.findById(coachId)
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.RESOURCE_NOT_FOUND));
        return CoachInviteDto.from(saved, coach, athlete);
    }

    // ── athlete inbox ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CoachInviteDto> listIncoming(Long athleteId) {
        List<CoachAthlete> rows =
                coachAthleteRepository.findByAthleteIdAndStatusOrderByIdDesc(athleteId, "pending");
        if (rows.isEmpty()) return List.of();

        // Batch-hydrate the coach users. The athlete side is the same
        // person across every row, so we only need it once.
        Map<Long, User> coaches = new HashMap<>();
        List<Long> coachIds = new ArrayList<>();
        for (CoachAthlete r : rows) coachIds.add(r.getCoachId());
        userRepository.findAllById(coachIds).forEach(u -> coaches.put(u.getId(), u));

        User athlete = userRepository.findById(athleteId)
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.RESOURCE_NOT_FOUND));

        List<CoachInviteDto> out = new ArrayList<>(rows.size());
        for (CoachAthlete r : rows) {
            out.add(CoachInviteDto.from(r, coaches.get(r.getCoachId()), athlete));
        }
        return out;
    }

    // ── accept / decline ──────────────────────────────────────────────────

    @Transactional
    public CoachInviteDto accept(Long athleteId, Long inviteId) {
        CoachAthlete row = loadInviteFor(athleteId, inviteId);
        row.setStatus("active");
        row.setSince(LocalDate.now(clock));
        return hydrate(coachAthleteRepository.save(row));
    }

    @Transactional
    public CoachInviteDto decline(Long athleteId, Long inviteId) {
        CoachAthlete row = loadInviteFor(athleteId, inviteId);
        row.setStatus("ended");
        return hydrate(coachAthleteRepository.save(row));
    }

    // ── AH-072 roster + my-coach ──────────────────────────────────────────

    /** Allowed status values for the roster filter. Defaults to "active". */
    private static final Set<String> ROSTER_STATUSES =
            Set.of("pending", "active", "ended");

    /** Allowed flag values per the schema CHECK. */
    private static final Set<String> ROSTER_FLAGS =
            Set.of("on_track", "attention", "risk");

    /**
     * Coach roster, cursor-paginated. Filters silently coerce out-of-set
     * values to defaults — same forgiving pattern as the feed type filter
     * (AH-062). The hydration pass batches the athlete loads in one
     * round-trip.
     */
    @Transactional(readOnly = true)
    public CursorPage<RosterEntryDto> listRoster(Long coachId, String statusParam,
                                                 String flagParam, Long cursor, int limit) {
        String status = (statusParam != null && ROSTER_STATUSES.contains(statusParam))
                ? statusParam : "active";
        String flag = (flagParam != null && ROSTER_FLAGS.contains(flagParam))
                ? flagParam : null;

        List<CoachAthlete> rows = coachAthleteRepository.findRoster(
                coachId, status, flag, cursor, PageRequest.of(0, limit + 1));
        if (rows.isEmpty()) return CursorPage.of(List.of(), null);

        boolean hasMore = rows.size() > limit;
        List<CoachAthlete> visible = hasMore ? rows.subList(0, limit) : rows;

        // Batch-load athletes.
        Set<Long> athleteIds = new HashSet<>();
        for (CoachAthlete r : visible) athleteIds.add(r.getAthleteId());
        Map<Long, User> athletesById = new HashMap<>();
        userRepository.findAllById(athleteIds).forEach(u -> athletesById.put(u.getId(), u));

        List<RosterEntryDto> items = new ArrayList<>(visible.size());
        for (CoachAthlete r : visible) {
            items.add(RosterEntryDto.from(r, athletesById.get(r.getAthleteId())));
        }
        String nextCursor = hasMore
                ? String.valueOf(visible.get(visible.size() - 1).getId())
                : null;
        return CursorPage.of(items, nextCursor);
    }

    /**
     * Athlete-side: the caller's active coach relationship, hydrated.
     * Returns null when the athlete has no active coach.
     */
    @Transactional(readOnly = true)
    public MyCoachDto getMyCoach(Long athleteId) {
        return coachAthleteRepository
                .findFirstByAthleteIdAndStatus(athleteId, "active")
                .map(row -> {
                    User coach = userRepository.findById(row.getCoachId()).orElse(null);
                    return MyCoachDto.from(row, coach);
                })
                .orElse(null);
    }

    // ── AH-073 student detail aggregate ───────────────────────────────────

    /**
     * Coach's deep-dive view of one athlete. Visibility gate: the caller
     * must be the coach on an {@code active} relationship — "not yours"
     * and "doesn't exist" both return {@code RESOURCE_NOT_FOUND} (404)
     * so the API doesn't leak existence via differential status. Then
     * composes the latest evaluation + weekly cardio summary + the last
     * 5 sessions by reusing the existing rollup readers.
     */
    @Transactional(readOnly = true)
    public StudentDetailDto getStudentDetail(Long coachId, Long athleteId) {
        CoachAthlete row = coachAthleteRepository
                .findByCoachIdAndAthleteId(coachId, athleteId)
                .filter(r -> "active".equals(r.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.RESOURCE_NOT_FOUND));

        User athlete = userRepository.findById(athleteId)
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.RESOURCE_NOT_FOUND));

        // Reuse the existing rollup endpoints' read paths.
        List<WorkoutSessionSummaryDto> recentSessions =
                trainingService.listRecentSessions(athleteId, null, 5).items();
        WeeklySummaryDto weekly = trainingService.getWeeklySummary(athleteId);
        List<EvaluationSummaryDto> latest = evaluationService.listRecent(athleteId, null, 1).items();

        return new StudentDetailDto(
                row.getId(),
                row.getStatus(),
                row.getSince(),
                row.getGoal(),
                row.getFlag(),
                row.getAdherencePct(),
                row.getLastActivityAt(),
                PublicUserDto.from(athlete),
                latest.isEmpty() ? null : latest.get(0),
                weekly,
                recentSessions);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    /**
     * Loads an invite that's addressed to the caller AND still pending.
     * "Not yours" / "doesn't exist" return the same code so the API
     * doesn't leak existence via timing. "Not pending" returns a distinct
     * code so the client can render an honest "already accepted" message.
     */
    private CoachAthlete loadInviteFor(Long athleteId, Long inviteId) {
        CoachAthlete row = coachAthleteRepository.findById(inviteId)
                .filter(r -> r.getAthleteId().equals(athleteId))
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.INVITE_NOT_FOUND));
        if (!"pending".equals(row.getStatus())) {
            throw new ConflictException(MessageCode.INVITE_NOT_PENDING);
        }
        return row;
    }

    private CoachInviteDto hydrate(CoachAthlete row) {
        User coach = userRepository.findById(row.getCoachId()).orElse(null);
        User athlete = userRepository.findById(row.getAthleteId()).orElse(null);
        return CoachInviteDto.from(row, coach, athlete);
    }

    // ── AH-075 coach profile (read + upsert) ──────────────────────────────

    /**
     * Returns the caller's coach card. Lazy upsert pattern — when the
     * caller has never edited their card, returns a zeroed default DTO so
     * the client can render placeholders without a 404.
     */
    @Transactional(readOnly = true)
    public CoachProfileDto getMyCoachProfile(Long userId) {
        return coachProfileRepository.findById(userId)
                .map(CoachProfileDto::from)
                .orElseGet(() -> new CoachProfileDto(userId, null, null, 0, null, 0));
    }

    /**
     * Upsert the caller's coach card. Athlete-count + ratings columns are
     * server-maintained and intentionally not editable through the
     * payload.
     */
    @Transactional
    public CoachProfileDto upsertMyCoachProfile(Long userId, UpdateCoachProfileRequest request) {
        CoachProfile row = coachProfileRepository.findById(userId)
                .orElseGet(() -> CoachProfile.builder()
                        .userId(userId)
                        .athleteCount(0)
                        .ratingCount(0)
                        .build());
        if (request.getHeadline() != null) row.setHeadline(trimToNull(request.getHeadline()));
        if (request.getYearsExperience() != null) row.setYearsExperience(request.getYearsExperience());
        return CoachProfileDto.from(coachProfileRepository.save(row));
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
