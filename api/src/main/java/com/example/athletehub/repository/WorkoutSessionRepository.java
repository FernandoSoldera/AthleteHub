package com.example.athletehub.repository;

import com.example.athletehub.model.WorkoutSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, Long> {

    /**
     * The user's currently-in-progress session, if any. Backed by the
     * partial index {@code idx_workout_sessions_user_active}; we only ever
     * allow one such row per user (enforced by the service, not the schema
     * — a CHECK across rows isn't practical in standard SQL).
     */
    Optional<WorkoutSession> findFirstByUserIdAndStatus(Long userId, String status);
}
