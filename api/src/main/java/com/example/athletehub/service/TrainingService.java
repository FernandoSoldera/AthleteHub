package com.example.athletehub.service;

import com.example.athletehub.dto.ExerciseSetDto;
import com.example.athletehub.dto.PatchSessionRequest;
import com.example.athletehub.dto.SessionExerciseDto;
import com.example.athletehub.dto.SetOpRequest;
import com.example.athletehub.dto.StartSessionRequest;
import com.example.athletehub.dto.TemplateExerciseDto;
import com.example.athletehub.dto.TodayPlanResponse;
import com.example.athletehub.dto.WorkoutSessionDto;
import com.example.athletehub.dto.WorkoutTemplateDto;
import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.exception.BadRequestException;
import com.example.athletehub.exception.ConflictException;
import com.example.athletehub.exception.ResourceNotFoundException;
import com.example.athletehub.model.Exercise;
import com.example.athletehub.model.ExerciseSet;
import com.example.athletehub.model.PersonalRecord;
import com.example.athletehub.model.SessionExercise;
import com.example.athletehub.model.WorkoutSession;
import com.example.athletehub.model.WorkoutTemplate;
import com.example.athletehub.model.WorkoutTemplateExercise;
import com.example.athletehub.repository.ExerciseRepository;
import com.example.athletehub.repository.ExerciseSetRepository;
import com.example.athletehub.repository.PersonalRecordRepository;
import com.example.athletehub.repository.SessionExerciseRepository;
import com.example.athletehub.repository.UserCountersRepository;
import com.example.athletehub.repository.WorkoutSessionRepository;
import com.example.athletehub.repository.WorkoutTemplateExerciseRepository;
import com.example.athletehub.repository.WorkoutTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Training-day orchestration: today's plan + start session (AH-032), then
 * the live-workout vocabulary — patch sets + finish session (AH-033).
 *
 * <p>The patch flow is idempotent on the natural key {@code
 * (sessionExerciseId, setNumber)} — clients can replay a tap without
 * fearing dupes, and a flaky network costs at most the in-flight op.
 *
 * <p>The finish flow recomputes rollups server-side ({@code total_sets},
 * {@code total_volume_kg}, {@code pr_count}) regardless of any running
 * client estimate, detects per-exercise PRs on the e1RM (Epley) and
 * max-weight metrics, upserts {@code personal_records}, and stamps
 * {@code is_pr} on the responsible set so the UI can highlight specific
 * lines without joining back through the PR table.
 *
 * <p>The cross-row "at most one in-progress session per user" rule lives
 * here, not in the schema (standard SQL can't express it as a CHECK).
 */
@Service
@RequiredArgsConstructor
public class TrainingService {

    /** Epley's e1RM coefficient. 30 means "reps / 30" in the formula. */
    private static final BigDecimal EPLEY_DIVISOR = BigDecimal.valueOf(30);

    private final WorkoutTemplateRepository templateRepository;
    private final WorkoutTemplateExerciseRepository templateExerciseRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final SessionExerciseRepository sessionExerciseRepository;
    private final ExerciseSetRepository exerciseSetRepository;
    private final PersonalRecordRepository personalRecordRepository;
    private final ExerciseRepository exerciseRepository;
    private final UserCountersRepository userCountersRepository;
    private final Clock clock;

    // ── AH-032 — today's plan ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TodayPlanResponse getTodayPlan(Long userId) {
        short today = (short) LocalDate.now(clock).getDayOfWeek().getValue();

        List<WorkoutTemplate> templates = templateRepository.findScheduledFor(
                userId, today, PageRequest.of(0, 1));
        WorkoutTemplateDto templateDto = templates.isEmpty()
                ? null
                : toTemplateDto(templates.get(0));

        Long activeSessionId = sessionRepository
                .findFirstByUserIdAndStatus(userId, "in_progress")
                .map(WorkoutSession::getId)
                .orElse(null);

        return new TodayPlanResponse(templateDto, activeSessionId);
    }

    // ── AH-032 — start session ────────────────────────────────────────────

    @Transactional
    public WorkoutSessionDto startSession(Long userId, StartSessionRequest request) {
        sessionRepository.findFirstByUserIdAndStatus(userId, "in_progress")
                .ifPresent(existing -> {
                    throw new ConflictException(MessageCode.ACTIVE_SESSION_EXISTS);
                });

        WorkoutTemplate template = null;
        if (request != null && request.getTemplateId() != null) {
            template = templateRepository.findById(request.getTemplateId())
                    .filter(t -> t.getOwnerId().equals(userId))
                    .orElseThrow(() -> new ResourceNotFoundException(MessageCode.TEMPLATE_NOT_FOUND));
        }

        String title = (request != null && request.getTitle() != null && !request.getTitle().isBlank())
                ? request.getTitle().trim()
                : (template != null ? template.getName() : "Workout");

        WorkoutSession session = sessionRepository.save(WorkoutSession.builder()
                .userId(userId)
                .templateId(template != null ? template.getId() : null)
                .title(title)
                .build());

        List<SessionExercise> seeded = List.of();
        if (template != null) {
            List<WorkoutTemplateExercise> slots =
                    templateExerciseRepository.findByTemplateIdOrderByPositionAsc(template.getId());
            seeded = slots.stream()
                    .map(slot -> SessionExercise.builder()
                            .sessionId(session.getId())
                            .exerciseId(slot.getExerciseId())
                            .position(slot.getPosition())
                            .scheme(slot.getScheme())
                            // We don't try to parse '80 kg' into a number;
                            // the user enters real weights per set anyway.
                            .targetWeight(null)
                            .build())
                    .toList();
            sessionExerciseRepository.saveAll(seeded);
        }

        return toSessionDto(session, seeded, Map.of());
    }

    // ── AH-033 — patch session (set ops) ──────────────────────────────────

    @Transactional
    public WorkoutSessionDto patchSession(Long userId, Long sessionId, PatchSessionRequest request) {
        WorkoutSession session = ownedSessionOrThrow(userId, sessionId);
        if (!"in_progress".equals(session.getStatus())) {
            throw new ConflictException(MessageCode.SESSION_NOT_IN_PROGRESS);
        }

        // Pre-validate: every op's sessionExerciseId must belong to this session.
        List<SessionExercise> sessionExercises =
                sessionExerciseRepository.findBySessionIdOrderByPositionAsc(sessionId);
        Set<Long> validSeIds = new HashSet<>();
        for (SessionExercise se : sessionExercises) validSeIds.add(se.getId());

        OffsetDateTime now = OffsetDateTime.now(clock);
        for (SetOpRequest op : request.getSets()) {
            if (!validSeIds.contains(op.getSessionExerciseId())) {
                throw new BadRequestException(MessageCode.INVALID_SET_OP);
            }
            applyOp(op, now);
        }

        return hydrateSession(session, sessionExercises);
    }

    private void applyOp(SetOpRequest op, OffsetDateTime now) {
        switch (op.getOp()) {
            case "upsert" -> {
                ExerciseSet existing = exerciseSetRepository
                        .findBySessionExerciseIdAndSetNumber(op.getSessionExerciseId(), op.getSetNumber())
                        .orElse(null);
                boolean nowDone = Boolean.TRUE.equals(op.getDone());
                if (existing == null) {
                    exerciseSetRepository.save(ExerciseSet.builder()
                            .sessionExerciseId(op.getSessionExerciseId())
                            .setNumber(op.getSetNumber())
                            .weightKg(op.getWeightKg())
                            .reps(op.getReps())
                            .rpe(op.getRpe())
                            .done(nowDone)
                            .pr(false)
                            .completedAt(nowDone ? now : null)
                            .build());
                } else {
                    // Only overwrite fields that were supplied — null means
                    // "leave it" for everything except `done`, where
                    // false-vs-absent matters too much to coalesce.
                    if (op.getWeightKg() != null) existing.setWeightKg(op.getWeightKg());
                    if (op.getReps() != null) existing.setReps(op.getReps());
                    if (op.getRpe() != null) existing.setRpe(op.getRpe());
                    if (op.getDone() != null) {
                        boolean wasDone = existing.isDone();
                        existing.setDone(nowDone);
                        if (nowDone && !wasDone) existing.setCompletedAt(now);
                        if (!nowDone) existing.setCompletedAt(null);
                    }
                    exerciseSetRepository.save(existing);
                }
            }
            case "delete" -> exerciseSetRepository
                    .findBySessionExerciseIdAndSetNumber(op.getSessionExerciseId(), op.getSetNumber())
                    .ifPresent(exerciseSetRepository::delete);
            default -> throw new BadRequestException(MessageCode.INVALID_SET_OP);
        }
    }

    // ── AH-033 — finish session ───────────────────────────────────────────

    @Transactional
    public WorkoutSessionDto finishSession(Long userId, Long sessionId) {
        WorkoutSession session = ownedSessionOrThrow(userId, sessionId);
        if (!"in_progress".equals(session.getStatus())) {
            throw new ConflictException(MessageCode.SESSION_NOT_IN_PROGRESS);
        }

        List<SessionExercise> sessionExercises =
                sessionExerciseRepository.findBySessionIdOrderByPositionAsc(sessionId);
        List<Long> seIds = sessionExercises.stream().map(SessionExercise::getId).toList();
        List<ExerciseSet> allSets = seIds.isEmpty()
                ? List.of()
                : exerciseSetRepository
                        .findBySessionExerciseIdInOrderBySessionExerciseIdAscSetNumberAsc(seIds);

        // Group done sets by session_exercise (and therefore by exercise id).
        Map<Long, Long> seToExercise = new HashMap<>();
        for (SessionExercise se : sessionExercises) seToExercise.put(se.getId(), se.getExerciseId());

        BigDecimal totalVolume = BigDecimal.ZERO;
        int totalSets = 0;
        Map<Long, List<ExerciseSet>> doneByExercise = new HashMap<>();
        for (ExerciseSet s : allSets) {
            if (!s.isDone() || s.getWeightKg() == null || s.getReps() == null) continue;
            totalSets++;
            totalVolume = totalVolume.add(
                    s.getWeightKg().multiply(BigDecimal.valueOf(s.getReps())));
            Long exId = seToExercise.get(s.getSessionExerciseId());
            doneByExercise.computeIfAbsent(exId, k -> new ArrayList<>()).add(s);
        }

        // PR detection — batch-load current PRs for all exercises touched.
        OffsetDateTime now = OffsetDateTime.now(clock);
        int prCount = detectAndUpsertPRs(userId, session.getId(), doneByExercise, now);

        session.setStatus("completed");
        session.setEndedAt(now);
        session.setDurationSeconds(
                (int) Duration.between(session.getStartedAt().toInstant(), now.toInstant()).getSeconds());
        session.setTotalVolumeKg(totalVolume.setScale(2, RoundingMode.HALF_UP));
        session.setTotalSets(totalSets);
        session.setPrCount(prCount);
        sessionRepository.save(session);

        userCountersRepository.adjustSessions(userId, 1);

        return hydrateSession(session, sessionExercises);
    }

    /**
     * For each exercise with at least one done set, look at the best set on
     * e1RM and the heaviest weight set; compare against the user's prior
     * personal_records on those metrics; upsert when improved and flag the
     * responsible set's {@code is_pr}. Returns the count of distinct
     * (exercise, metric) pairs that newly became PRs in this session.
     */
    private int detectAndUpsertPRs(Long userId, Long sessionId,
                                   Map<Long, List<ExerciseSet>> doneByExercise,
                                   OffsetDateTime achievedAt) {
        if (doneByExercise.isEmpty()) return 0;

        List<Long> exerciseIds = new ArrayList<>(doneByExercise.keySet());
        Map<String, PersonalRecord> existing = new HashMap<>();
        for (PersonalRecord pr : personalRecordRepository.findByUserIdAndExerciseIdIn(userId, exerciseIds)) {
            existing.put(pr.getExerciseId() + ":" + pr.getMetric(), pr);
        }

        int newPRs = 0;
        Set<Long> setsToFlag = new HashSet<>();

        for (Map.Entry<Long, List<ExerciseSet>> entry : doneByExercise.entrySet()) {
            Long exerciseId = entry.getKey();
            List<ExerciseSet> sets = entry.getValue();

            // e1RM (Epley): weight * (1 + reps / 30). Best across sets.
            ExerciseSet bestE1rmSet = null;
            BigDecimal bestE1rm = null;
            // Max weight (with reps >= 1, which is already true for done sets).
            ExerciseSet maxWeightSet = null;
            BigDecimal maxWeight = null;

            for (ExerciseSet s : sets) {
                BigDecimal e1rm = epley(s.getWeightKg(), s.getReps());
                if (bestE1rm == null || e1rm.compareTo(bestE1rm) > 0) {
                    bestE1rm = e1rm;
                    bestE1rmSet = s;
                }
                if (maxWeight == null || s.getWeightKg().compareTo(maxWeight) > 0) {
                    maxWeight = s.getWeightKg();
                    maxWeightSet = s;
                }
            }

            // Compare against existing PRs and upsert when beaten.
            if (upsertIfBetter(userId, exerciseId, "e1rm", bestE1rm, sessionId, achievedAt, existing)) {
                setsToFlag.add(bestE1rmSet.getId());
                newPRs++;
            }
            if (upsertIfBetter(userId, exerciseId, "max_weight", maxWeight, sessionId, achievedAt, existing)) {
                setsToFlag.add(maxWeightSet.getId());
                newPRs++;
            }
        }

        // Mark the responsible sets so the UI can highlight specific lines.
        if (!setsToFlag.isEmpty()) {
            List<ExerciseSet> flagged = exerciseSetRepository.findAllById(setsToFlag);
            for (ExerciseSet s : flagged) s.setPr(true);
            exerciseSetRepository.saveAll(flagged);
        }
        return newPRs;
    }

    private boolean upsertIfBetter(Long userId, Long exerciseId, String metric, BigDecimal candidate,
                                   Long sessionId, OffsetDateTime achievedAt,
                                   Map<String, PersonalRecord> existing) {
        if (candidate == null) return false;
        BigDecimal rounded = candidate.setScale(2, RoundingMode.HALF_UP);
        PersonalRecord prior = existing.get(exerciseId + ":" + metric);
        if (prior == null) {
            personalRecordRepository.save(PersonalRecord.builder()
                    .userId(userId)
                    .exerciseId(exerciseId)
                    .metric(metric)
                    .value(rounded)
                    .achievedAt(achievedAt)
                    .sessionId(sessionId)
                    .build());
            return true;
        }
        if (rounded.compareTo(prior.getValue()) > 0) {
            prior.setValue(rounded);
            prior.setAchievedAt(achievedAt);
            prior.setSessionId(sessionId);
            personalRecordRepository.save(prior);
            return true;
        }
        return false;
    }

    private static BigDecimal epley(BigDecimal weight, int reps) {
        // weight * (1 + reps/30); one rep equals the weight itself.
        return weight.multiply(BigDecimal.ONE.add(
                BigDecimal.valueOf(reps).divide(EPLEY_DIVISOR, 4, RoundingMode.HALF_UP)));
    }

    // ── ownership + DTO assembly ──────────────────────────────────────────

    private WorkoutSession ownedSessionOrThrow(Long userId, Long sessionId) {
        return sessionRepository.findById(sessionId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.SESSION_NOT_FOUND));
    }

    private WorkoutSessionDto hydrateSession(WorkoutSession session, List<SessionExercise> sessionExercises) {
        List<Long> seIds = sessionExercises.stream().map(SessionExercise::getId).toList();
        List<ExerciseSet> sets = seIds.isEmpty()
                ? List.of()
                : exerciseSetRepository
                        .findBySessionExerciseIdInOrderBySessionExerciseIdAscSetNumberAsc(seIds);
        Map<Long, List<ExerciseSet>> bySe = new HashMap<>();
        for (ExerciseSet s : sets) bySe.computeIfAbsent(s.getSessionExerciseId(), k -> new ArrayList<>()).add(s);
        return toSessionDto(session, sessionExercises, bySe);
    }

    private WorkoutTemplateDto toTemplateDto(WorkoutTemplate template) {
        List<WorkoutTemplateExercise> slots =
                templateExerciseRepository.findByTemplateIdOrderByPositionAsc(template.getId());
        Map<Long, String> names = exerciseNames(slots.stream().map(WorkoutTemplateExercise::getExerciseId).toList());
        List<TemplateExerciseDto> exercises = slots.stream()
                .map(slot -> new TemplateExerciseDto(
                        slot.getExerciseId(),
                        names.getOrDefault(slot.getExerciseId(), ""),
                        slot.getPosition(),
                        slot.getScheme(),
                        slot.getTarget()))
                .toList();
        return new WorkoutTemplateDto(
                template.getId(),
                template.getName(),
                template.getDescription(),
                exercises);
    }

    private WorkoutSessionDto toSessionDto(WorkoutSession session,
                                           List<SessionExercise> sessionExercises,
                                           Map<Long, List<ExerciseSet>> setsBySe) {
        Map<Long, String> names = exerciseNames(
                sessionExercises.stream().map(SessionExercise::getExerciseId).toList());
        List<SessionExerciseDto> items = sessionExercises.stream()
                .map(se -> new SessionExerciseDto(
                        se.getId(),
                        se.getExerciseId(),
                        names.getOrDefault(se.getExerciseId(), ""),
                        se.getPosition(),
                        se.getScheme(),
                        se.getTargetWeight(),
                        setsBySe.getOrDefault(se.getId(), List.of()).stream()
                                .map(TrainingService::toSetDto)
                                .toList()))
                .toList();
        return new WorkoutSessionDto(
                session.getId(),
                session.getTemplateId(),
                session.getTitle(),
                session.getStatus(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getDurationSeconds(),
                session.getTotalVolumeKg(),
                session.getTotalSets(),
                session.getPrCount(),
                items);
    }

    private static ExerciseSetDto toSetDto(ExerciseSet s) {
        return new ExerciseSetDto(
                s.getId(),
                s.getSessionExerciseId(),
                s.getSetNumber(),
                s.getWeightKg(),
                s.getReps(),
                s.getRpe(),
                s.isDone(),
                s.isPr(),
                s.getCompletedAt());
    }

    private Map<Long, String> exerciseNames(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, String> out = new HashMap<>();
        for (Exercise e : exerciseRepository.findAllById(ids)) {
            out.put(e.getId(), e.getName());
        }
        return out;
    }
}
