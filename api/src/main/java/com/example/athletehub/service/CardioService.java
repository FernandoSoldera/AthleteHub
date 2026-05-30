package com.example.athletehub.service;

import com.example.athletehub.dto.CardioActivityDto;
import com.example.athletehub.dto.CreateCardioRequest;
import com.example.athletehub.dto.CursorPage;
import com.example.athletehub.model.CardioActivity;
import com.example.athletehub.repository.CardioActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cardio logging service (AH-034). Two flows:
 *
 * <ul>
 *   <li><b>create</b> — straight write. The schema enforces the value
 *       ranges; bean validation on {@link CreateCardioRequest} keeps bad
 *       payloads at 400 instead of letting them reach the DB.</li>
 *   <li><b>listRecent</b> — newest-first cursor pagination on id DESC.
 *       Same limit + 1 trick as the rest of the codebase.</li>
 * </ul>
 *
 * <p>Source defaults to {@code "self"} server-side. {@code import} arrives
 * with wearable sync; {@code assigned} with Epic 7 coach assignments —
 * both will route through dedicated endpoints rather than this generic
 * create.
 */
@Service
@RequiredArgsConstructor
public class CardioService {

    private final CardioActivityRepository cardioRepository;
    private final PostService postService;

    @Transactional
    public CardioActivityDto create(Long userId, CreateCardioRequest request) {
        CardioActivity saved = cardioRepository.save(CardioActivity.builder()
                .userId(userId)
                .type(request.getType())
                .distanceM(request.getDistanceM())
                .durationSeconds(request.getDurationSeconds())
                .avgPaceSPerKm(request.getAvgPaceSPerKm())
                .avgPowerW(request.getAvgPowerW())
                .avgHr(request.getAvgHr())
                .maxHr(request.getMaxHr())
                .elevationGainM(request.getElevationGainM())
                .kcal(request.getKcal())
                .notes(request.getNotes())
                .startedAt(request.getStartedAt())  // null → @PrePersist defaults to now()
                .source("self")
                .build());
        // Auto-publish a feed card. Wrapped so a snapshot failure can't
        // roll back the cardio row the user just logged.
        try {
            postService.publishFromCardio(saved);
        } catch (RuntimeException ignored) {
            // Intentional: the originating transaction wins.
        }
        return CardioActivityDto.from(saved);
    }

    @Transactional(readOnly = true)
    public CursorPage<CardioActivityDto> listRecent(Long userId, Long cursor, int limit) {
        List<CardioActivity> rows = cardioRepository.findRecent(
                userId, cursor, PageRequest.of(0, limit + 1));
        boolean hasMore = rows.size() > limit;
        List<CardioActivity> visible = hasMore ? rows.subList(0, limit) : rows;
        List<CardioActivityDto> items = visible.stream().map(CardioActivityDto::from).toList();
        String nextCursor = hasMore
                ? String.valueOf(visible.get(visible.size() - 1).getId())
                : null;
        return CursorPage.of(items, nextCursor);
    }
}
