package com.example.athletehub.repository;

import com.example.athletehub.model.Food;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FoodRepository extends JpaRepository<Food, Long> {

    /**
     * Visibility-filtered listing without a name filter. Same split as the
     * exercise catalog — passing a null {@code q} into PostgreSQL via JPQL
     * {@code CONCAT('%', :q, '%')} trips up type inference (it lands on
     * {@code lower(bytea)} which doesn't exist). Keeping the two branches
     * in separate queries means we never pass a null where a text type is
     * required.
     *
     * <p>Cursor pagination is ascending on {@code id} — globals were
     * seeded first (low ids) and customs follow (high ids), so the
     * natural order puts the catalog first then the user's own additions.
     * The service fetches {@code limit + 1} and uses the extra row to
     * tell "more pages" from "exactly limit, no more".
     */
    @Query("""
            SELECT f FROM Food f
            WHERE (f.global = true OR f.createdBy = :userId)
              AND (:cursor IS NULL OR f.id > :cursor)
            ORDER BY f.id ASC
            """)
    List<Food> searchVisible(@Param("userId") Long userId,
                             @Param("cursor") Long cursor,
                             Pageable pageable);

    /**
     * Same visibility rule, with case-insensitive substring matching on
     * the food name so "chick" finds "Chicken Breast".
     */
    @Query("""
            SELECT f FROM Food f
            WHERE (f.global = true OR f.createdBy = :userId)
              AND (:cursor IS NULL OR f.id > :cursor)
              AND LOWER(f.name) LIKE LOWER(CONCAT('%', :q, '%'))
            ORDER BY f.id ASC
            """)
    List<Food> searchVisibleByName(@Param("userId") Long userId,
                                   @Param("q") String q,
                                   @Param("cursor") Long cursor,
                                   Pageable pageable);

    /**
     * Used by the create-custom path to reject obvious duplicates against
     * the caller's own customs (case-insensitive). We don't dedupe against
     * the global catalog — overlap is fine if the user wants their own
     * variant ("Chicken Breast (raw, my batch)").
     */
    Optional<Food> findFirstByCreatedByAndNameIgnoreCase(Long createdBy, String name);

    /**
     * Same visibility rule as the search ({@code is_global = true OR
     * created_by = userId}). Used by the diary / favorites endpoints to
     * reject references to foods the caller can't see — returns empty
     * instead of leaking another user's customs.
     */
    @Query("""
            SELECT f FROM Food f
            WHERE f.id = :foodId
              AND (f.global = true OR f.createdBy = :userId)
            """)
    Optional<Food> findByIdAndVisibleTo(@Param("foodId") Long foodId,
                                        @Param("userId") Long userId);
}
