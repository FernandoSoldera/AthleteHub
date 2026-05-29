package com.example.athletehub.repository;

import com.example.athletehub.model.WorkoutTemplateExercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkoutTemplateExerciseRepository extends JpaRepository<WorkoutTemplateExercise, Long> {

    /** Exercises of {@code templateId}, ordered by position. */
    List<WorkoutTemplateExercise> findByTemplateIdOrderByPositionAsc(Long templateId);
}
