package com.example.athletehub.repository;

import com.example.athletehub.model.Evaluation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {

    /**
     * Recent evaluations for {@code userId}, cursor-paginated on
     * {@code id DESC}. Surrogate-id cursor (not {@code evaluated_at}) so
     * backfilled rows land at the top of the list — matches the
     * "I just logged Friday's assessment on Monday" UX from cardio.
     */
    @Query("""
            SELECT e FROM Evaluation e
            WHERE e.userId = :userId
              AND (:beforeId IS NULL OR e.id < :beforeId)
            ORDER BY e.id DESC
            """)
    List<Evaluation> findRecent(@Param("userId") Long userId,
                                @Param("beforeId") Long beforeId,
                                Pageable pageable);

    /**
     * Evaluations for {@code userId} in {@code [start, end)} ordered
     * oldest → newest. Used by the metric-series endpoint so the chart
     * gets points already sorted on the time axis.
     */
    @Query("""
            SELECT e FROM Evaluation e
            WHERE e.userId = :userId
              AND e.evaluatedAt >= :start
              AND e.evaluatedAt <  :end
            ORDER BY e.evaluatedAt ASC
            """)
    List<Evaluation> findByUserInRange(@Param("userId") Long userId,
                                       @Param("start") OffsetDateTime start,
                                       @Param("end") OffsetDateTime end);
}
