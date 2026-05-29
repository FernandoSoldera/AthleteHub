package com.example.athletehub.repository;

import com.example.athletehub.model.ExerciseSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExerciseSetRepository extends JpaRepository<ExerciseSet, Long> {

    /** Lookup by the natural key {@code (session_exercise_id, set_number)}. */
    Optional<ExerciseSet> findBySessionExerciseIdAndSetNumber(Long sessionExerciseId, int setNumber);

    /** Sets belonging to any of the given session_exercises, ordered for display. */
    List<ExerciseSet> findBySessionExerciseIdInOrderBySessionExerciseIdAscSetNumberAsc(
            List<Long> sessionExerciseIds);
}
