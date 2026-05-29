package com.example.athletehub.repository;

import com.example.athletehub.model.EvaluationMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EvaluationMeasurementRepository extends JpaRepository<EvaluationMeasurement, Long> {

    /** All measurements for one evaluation, ordered by point_id for stable display. */
    List<EvaluationMeasurement> findByEvaluationIdOrderByPointIdAsc(Long evaluationId);

    /**
     * Pulls one specific point across a batch of evaluations — used by the
     * metric-series endpoint to hydrate a single line on the chart in one
     * round trip instead of N point lookups.
     */
    @Query("""
            SELECT m FROM EvaluationMeasurement m
            WHERE m.evaluationId IN :evaluationIds
              AND m.pointId = :pointId
            """)
    List<EvaluationMeasurement> findByEvaluationIdInAndPointId(
            @Param("evaluationIds") List<Long> evaluationIds,
            @Param("pointId") String pointId);
}
