package com.example.athletehub.service;

import com.example.athletehub.dto.CreateExerciseRequest;
import com.example.athletehub.dto.CursorPage;
import com.example.athletehub.dto.ExerciseDto;
import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.exception.ConflictException;
import com.example.athletehub.model.Exercise;
import com.example.athletehub.repository.ExerciseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Exercise catalog service (AH-031). Two flows:
 *
 * <ul>
 *   <li><b>Search</b> — global rows + the caller's own customs, optionally
 *       filtered by case-insensitive substring on {@code name}. Cursor
 *       pagination on id-ASC keeps catalog entries first then user
 *       additions, with the {@code limit + 1} trick to know if there's
 *       another page.</li>
 *   <li><b>Create custom</b> — a user-private row. The schema's XOR
 *       constraint guarantees a custom row must have {@code createdBy}
 *       set; the service trims the name and rejects duplicates against
 *       the caller's own customs (not against globals — overlap there
 *       is intentional so people can fork a global with their own cue).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ExerciseService {

    private final ExerciseRepository exerciseRepository;

    @Transactional(readOnly = true)
    public CursorPage<ExerciseDto> search(Long userId, String q, Long cursor, int limit) {
        String trimmed = (q == null) ? null : q.trim();
        if (trimmed != null && trimmed.isEmpty()) trimmed = null;

        // Split queries — passing a null `q` into PostgreSQL via JPQL
        // CONCAT trips up type inference (lower(bytea) doesn't exist).
        List<Exercise> rows = (trimmed == null)
                ? exerciseRepository.searchVisible(userId, cursor, PageRequest.of(0, limit + 1))
                : exerciseRepository.searchVisibleByName(userId, trimmed, cursor, PageRequest.of(0, limit + 1));

        boolean hasMore = rows.size() > limit;
        List<Exercise> visible = hasMore ? rows.subList(0, limit) : rows;
        List<ExerciseDto> items = visible.stream().map(ExerciseDto::from).toList();
        String nextCursor = hasMore
                ? String.valueOf(visible.get(visible.size() - 1).getId())
                : null;
        return CursorPage.of(items, nextCursor);
    }

    @Transactional
    public ExerciseDto createCustom(Long userId, CreateExerciseRequest request) {
        String name = request.getName().trim();

        // Reject only against the caller's own customs — see class doc.
        exerciseRepository.findFirstByCreatedByAndNameIgnoreCase(userId, name)
                .ifPresent(e -> {
                    throw new ConflictException(MessageCode.EXERCISE_ALREADY_EXISTS);
                });

        Exercise saved = exerciseRepository.save(Exercise.builder()
                .name(name)
                .category(trimToNull(request.getCategory()))
                .primaryMuscle(trimToNull(request.getPrimaryMuscle()))
                .equipment(trimToNull(request.getEquipment()))
                .global(false)
                .createdBy(userId)
                .build());

        return ExerciseDto.from(saved);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
