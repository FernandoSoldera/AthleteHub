package com.example.athletehub.repository;

import com.example.athletehub.model.CoachAthlete;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CoachAthleteRepository extends JpaRepository<CoachAthlete, Long> {

    /**
     * Used by invite + accept to find an existing relationship row before
     * deciding whether to revive (ended → pending) or block (pending /
     * active → 409). The schema's {@code UNIQUE(coach_id, athlete_id)}
     * guarantees at most one row per pair.
     */
    Optional<CoachAthlete> findByCoachIdAndAthleteId(Long coachId, Long athleteId);

    /**
     * Incoming invites for an athlete in the given status — typically
     * {@code "pending"} for the AH-071 inbox.
     */
    List<CoachAthlete> findByAthleteIdAndStatusOrderByIdDesc(Long athleteId, String status);

    /**
     * Coach roster (AH-072). Filtered by status (defaults to {@code "active"}
     * at the service layer) and an optional flag (null → all flags).
     * Cursor pagination on {@code id DESC} — newest relationship first,
     * since the most recent acceptance is typically what the coach
     * dashboard wants to highlight. Backed by
     * {@code idx_coach_athlete_coach_flag} from the AH-070 migration.
     */
    @Query("""
            SELECT r FROM CoachAthlete r
            WHERE r.coachId = :coachId
              AND r.status = :status
              AND (:flag IS NULL OR r.flag = :flag)
              AND (:cursor IS NULL OR r.id < :cursor)
            ORDER BY r.id DESC
            """)
    List<CoachAthlete> findRoster(@Param("coachId") Long coachId,
                                  @Param("status") String status,
                                  @Param("flag") String flag,
                                  @Param("cursor") Long cursor,
                                  Pageable pageable);

    /**
     * The athlete-side lookup for {@code GET /api/me/coach}. MVP assumes
     * one active coach per athlete (per {@code 02-data-model.md §4.8});
     * if the constraint ever relaxes we'd return all active rows here.
     */
    Optional<CoachAthlete> findFirstByAthleteIdAndStatus(Long athleteId, String status);
}
