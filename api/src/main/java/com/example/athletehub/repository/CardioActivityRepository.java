package com.example.athletehub.repository;

import com.example.athletehub.model.CardioActivity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public interface CardioActivityRepository extends JpaRepository<CardioActivity, Long> {

    /**
     * Recent activities for {@code userId}, cursor-paginated on {@code id DESC}.
     * Newest-first matches the Train screen's recent-cardio strip; using
     * surrogate id (not {@code started_at}) keeps the cursor stable when
     * users backfill old activities — those land with high ids and surface
     * at the top until manually reordered, which is the desired UX
     * ("I just logged my Sunday run on Tuesday — here it is").
     *
     * <p>The service fetches {@code limit + 1} and uses the extra row to
     * tell "more pages" from "exactly limit, no more".
     */
    @Query("""
            SELECT c FROM CardioActivity c
            WHERE c.userId = :userId
              AND (:beforeId IS NULL OR c.id < :beforeId)
            ORDER BY c.id DESC
            """)
    List<CardioActivity> findRecent(@Param("userId") Long userId,
                                    @Param("beforeId") Long beforeId,
                                    Pageable pageable);

    /**
     * Sum of {@code distance_m} for {@code userId} in the half-open window
     * {@code [start, end)}. Used by the weekly summary. Returns 0 when the
     * user has no activities in the range (COALESCE keeps the caller from
     * doing a null check).
     */
    @Query("""
            SELECT COALESCE(SUM(c.distanceM), 0) FROM CardioActivity c
            WHERE c.userId = :userId
              AND c.startedAt >= :start
              AND c.startedAt <  :end
            """)
    BigDecimal sumDistanceBetween(@Param("userId") Long userId,
                                  @Param("start") OffsetDateTime start,
                                  @Param("end") OffsetDateTime end);
}
