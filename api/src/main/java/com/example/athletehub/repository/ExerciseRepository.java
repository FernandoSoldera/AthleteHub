package com.example.athletehub.repository;

import com.example.athletehub.model.Exercise;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    /**
     * Visibility-filtered listing without a name filter.
     *
     * <p>Split from {@link #searchVisibleByName} because PostgreSQL can't
     * type-infer a {@code null} parameter inside {@code CONCAT('%', :q, '%')}
     * — it lands on {@code lower(bytea)} which doesn't exist. Keeping the
     * two branches in separate queries means we never pass a null where a
     * text type is required.
     *
     * <p>Cursor pagination is ascending on {@code id} — globals were
     * seeded first (low ids) and customs follow (high ids), so the
     * natural order puts the catalog first then the user's own additions.
     * The service fetches {@code limit + 1} and uses the extra row to
     * tell "more pages" from "exactly limit, no more".
     */
    @Query("""
            SELECT e FROM Exercise e
            WHERE (e.global = true OR e.createdBy = :userId)
              AND (:cursor IS NULL OR e.id > :cursor)
            ORDER BY e.id ASC
            """)
    List<Exercise> searchVisible(@Param("userId") Long userId,
                                 @Param("cursor") Long cursor,
                                 Pageable pageable);

    /**
     * Same visibility rule as {@link #searchVisible}, with a case-insensitive
     * substring match on the exercise name so "bench" finds "Bench Press"
     * and "Incline Bench Press".
     */
    @Query("""
            SELECT e FROM Exercise e
            WHERE (e.global = true OR e.createdBy = :userId)
              AND (:cursor IS NULL OR e.id > :cursor)
              AND LOWER(e.name) LIKE LOWER(CONCAT('%', :q, '%'))
            ORDER BY e.id ASC
            """)
    List<Exercise> searchVisibleByName(@Param("userId") Long userId,
                                      @Param("q") String q,
                                      @Param("cursor") Long cursor,
                                      Pageable pageable);

    /**
     * Used by the create-custom path to reject obvious duplicates against
     * the caller's own customs (case-insensitive). We don't dedupe against
     * the global catalog — overlap is fine if the user wants their own
     * variant ("Bench Press (chains)").
     */
    Optional<Exercise> findFirstByCreatedByAndNameIgnoreCase(Long createdBy, String name);
}
