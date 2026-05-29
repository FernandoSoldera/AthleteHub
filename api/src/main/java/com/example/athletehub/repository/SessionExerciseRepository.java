package com.example.athletehub.repository;

import com.example.athletehub.model.SessionExercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessionExerciseRepository extends JpaRepository<SessionExercise, Long> {

    /** Exercises of {@code sessionId}, ordered by position. */
    List<SessionExercise> findBySessionIdOrderByPositionAsc(Long sessionId);
}
