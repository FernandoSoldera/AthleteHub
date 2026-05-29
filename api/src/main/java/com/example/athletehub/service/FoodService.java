package com.example.athletehub.service;

import com.example.athletehub.dto.CreateFoodRequest;
import com.example.athletehub.dto.CursorPage;
import com.example.athletehub.dto.FoodDto;
import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.exception.ConflictException;
import com.example.athletehub.model.Food;
import com.example.athletehub.repository.FoodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Food catalog service (AH-051). Mirrors {@code ExerciseService} (AH-031):
 *
 * <ul>
 *   <li><b>search</b> — global rows + the caller's own customs, optionally
 *       filtered by case-insensitive substring on {@code name}. Cursor
 *       pagination on id-ASC keeps catalog entries first then user
 *       additions, with the {@code limit + 1} trick to know if there's
 *       another page.</li>
 *   <li><b>create custom</b> — a user-private row. The schema's XOR
 *       constraint guarantees a custom row must have {@code createdBy}
 *       set; the service trims the name and rejects duplicates against
 *       the caller's own customs (not against globals — overlap there
 *       is intentional so people can fork a global with their own batch).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class FoodService {

    private final FoodRepository foodRepository;

    @Transactional(readOnly = true)
    public CursorPage<FoodDto> search(Long userId, String q, Long cursor, int limit) {
        String trimmed = (q == null) ? null : q.trim();
        if (trimmed != null && trimmed.isEmpty()) trimmed = null;

        // Split queries — passing a null `q` into PostgreSQL via JPQL
        // CONCAT trips up type inference (lower(bytea) doesn't exist).
        List<Food> rows = (trimmed == null)
                ? foodRepository.searchVisible(userId, cursor, PageRequest.of(0, limit + 1))
                : foodRepository.searchVisibleByName(userId, trimmed, cursor, PageRequest.of(0, limit + 1));

        boolean hasMore = rows.size() > limit;
        List<Food> visible = hasMore ? rows.subList(0, limit) : rows;
        List<FoodDto> items = visible.stream().map(FoodDto::from).toList();
        String nextCursor = hasMore
                ? String.valueOf(visible.get(visible.size() - 1).getId())
                : null;
        return CursorPage.of(items, nextCursor);
    }

    @Transactional
    public FoodDto createCustom(Long userId, CreateFoodRequest request) {
        String name = request.getName().trim();

        // Reject only against the caller's own customs — see class doc.
        foodRepository.findFirstByCreatedByAndNameIgnoreCase(userId, name)
                .ifPresent(f -> {
                    throw new ConflictException(MessageCode.FOOD_ALREADY_EXISTS);
                });

        Food saved = foodRepository.save(Food.builder()
                .name(name)
                .brand(trimToNull(request.getBrand()))
                .global(false)
                .createdBy(userId)
                .servingSizeG(request.getServingSizeG())
                .kcal(request.getKcal())
                .proteinG(request.getProteinG())
                .carbG(request.getCarbG())
                .fatG(request.getFatG())
                .fiberG(request.getFiberG())
                .sodiumMg(request.getSodiumMg())
                .build());

        return FoodDto.from(saved);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
