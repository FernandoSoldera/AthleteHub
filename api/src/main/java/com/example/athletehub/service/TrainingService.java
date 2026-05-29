package com.example.athletehub.service;

import com.example.athletehub.dto.SessionExerciseDto;
import com.example.athletehub.dto.StartSessionRequest;
import com.example.athletehub.dto.TemplateExerciseDto;
import com.example.athletehub.dto.TodayPlanResponse;
import com.example.athletehub.dto.WorkoutSessionDto;
import com.example.athletehub.dto.WorkoutTemplateDto;
import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.exception.ConflictException;
import com.example.athletehub.exception.ResourceNotFoundException;
import com.example.athletehub.model.Exercise;
import com.example.athletehub.model.SessionExercise;
import com.example.athletehub.model.WorkoutSession;
import com.example.athletehub.model.WorkoutTemplate;
import com.example.athletehub.model.WorkoutTemplateExercise;
import com.example.athletehub.repository.ExerciseRepository;
import com.example.athletehub.repository.SessionExerciseRepository;
import com.example.athletehub.repository.WorkoutSessionRepository;
import com.example.athletehub.repository.WorkoutTemplateExerciseRepository;
import com.example.athletehub.repository.WorkoutTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Training-day orchestration (AH-032).
 *
 * <ul>
 *   <li><b>today's plan</b> — joins the user's templates to {@code
 *       template_schedules} for the current weekday and hydrates the
 *       exercise names from the catalog. Also reports any in-progress
 *       session so the hero card can show "Resume" instead of "Start".</li>
 *   <li><b>start session</b> — rejects a second start while one is
 *       already in progress (409 / {@code ACTIVE_SESSION_EXISTS}), then
 *       creates the session and seeds {@code session_exercises} from
 *       the template (if any) in one transaction.</li>
 * </ul>
 *
 * <p>The {@link Clock} dependency is here so tests can pin a deterministic
 * "today" instead of depending on the actual weekday the suite runs on.
 */
@Service
@RequiredArgsConstructor
public class TrainingService {

    private final WorkoutTemplateRepository templateRepository;
    private final WorkoutTemplateExerciseRepository templateExerciseRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final SessionExerciseRepository sessionExerciseRepository;
    private final ExerciseRepository exerciseRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TodayPlanResponse getTodayPlan(Long userId) {
        short today = (short) LocalDate.now(clock).getDayOfWeek().getValue();

        // First template scheduled for today (we sort by schedule id ASC).
        List<WorkoutTemplate> templates = templateRepository.findScheduledFor(
                userId, today, PageRequest.of(0, 1));
        WorkoutTemplateDto templateDto = templates.isEmpty()
                ? null
                : toTemplateDto(templates.get(0));

        // The active session is what the client uses to flip the hero CTA
        // from "Start" to "Resume" — we look it up regardless of whether a
        // plan exists today.
        Long activeSessionId = sessionRepository
                .findFirstByUserIdAndStatus(userId, "in_progress")
                .map(WorkoutSession::getId)
                .orElse(null);

        return new TodayPlanResponse(templateDto, activeSessionId);
    }

    @Transactional
    public WorkoutSessionDto startSession(Long userId, StartSessionRequest request) {
        // Reject double-start. The service enforces "at most one in_progress
        // per user" (a cross-row CHECK isn't practical in standard SQL).
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
                            // We don't try to parse "80 kg" into a number;
                            // the user enters real weights per set anyway.
                            .targetWeight(null)
                            .build())
                    .toList();
            sessionExerciseRepository.saveAll(seeded);
        }

        return toSessionDto(session, seeded);
    }

    // ── DTO assembly ──────────────────────────────────────────────────────

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

    private WorkoutSessionDto toSessionDto(WorkoutSession session, List<SessionExercise> seededExercises) {
        Map<Long, String> names = exerciseNames(
                seededExercises.stream().map(SessionExercise::getExerciseId).toList());
        List<SessionExerciseDto> items = seededExercises.stream()
                .map(se -> new SessionExerciseDto(
                        se.getId(),
                        se.getExerciseId(),
                        names.getOrDefault(se.getExerciseId(), ""),
                        se.getPosition(),
                        se.getScheme(),
                        se.getTargetWeight()))
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

    private Map<Long, String> exerciseNames(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, String> out = new HashMap<>();
        for (Exercise e : exerciseRepository.findAllById(ids)) {
            out.put(e.getId(), e.getName());
        }
        return out;
    }
}
