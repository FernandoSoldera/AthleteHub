package com.example.athletehub.repository;

import com.example.athletehub.model.EvaluationMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvaluationMeasurementRepository extends JpaRepository<EvaluationMeasurement, Long> {

    /** All measurements for one evaluation, ordered by point_id for stable display. */
    List<EvaluationMeasurement> findByEvaluationIdOrderByPointIdAsc(Long evaluationId);
}
