package com.example.athletehub.repository;

import com.example.athletehub.model.CoachAthlete;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
