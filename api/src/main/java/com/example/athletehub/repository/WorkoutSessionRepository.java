package com.example.athletehub.repository;

import com.example.athletehub.model.WorkoutSession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, Long> {

    /**
     * The user's currently-in-progress session, if any. Backed by the
     * partial index {@code idx_workout_sessions_user_active}; we only ever
     * allow one such row per user (enforced by the service, not the schema
     * — a CHECK across rows isn't practical in standard SQL).
     */
    Optional<WorkoutSession> findFirstByUserIdAndStatus(Long userId, String status);

    /**
     * Recent sessions for {@code userId}, cursor-paginated on {@code id DESC}.
     * Includes every status (in_progress and completed both surface — the
     * client can filter) so the recent list mirrors what actually happened.
     */
    @Query("""
            SELECT s FROM WorkoutSession s
            WHERE s.userId = :userId
              AND (:beforeId IS NULL OR s.id < :beforeId)
            ORDER BY s.id DESC
            """)
    List<WorkoutSession> findRecent(@Param("userId") Long userId,
                                    @Param("beforeId") Long beforeId,
                                    Pageable pageable);
}
