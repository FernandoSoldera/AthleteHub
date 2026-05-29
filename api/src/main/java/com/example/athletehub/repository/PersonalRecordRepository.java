package com.example.athletehub.repository;

import com.example.athletehub.model.PersonalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersonalRecordRepository extends JpaRepository<PersonalRecord, Long> {

    /**
     * Load all of a user's current PRs for the given exercises. Used by the
     * finish-session pass to decide whether each candidate beats the prior
     * best in a single round-trip instead of N point lookups.
     */
    List<PersonalRecord> findByUserIdAndExerciseIdIn(Long userId, List<Long> exerciseIds);
}
