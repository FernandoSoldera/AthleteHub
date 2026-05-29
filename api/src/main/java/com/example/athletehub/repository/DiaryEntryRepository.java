package com.example.athletehub.repository;

import com.example.athletehub.model.DiaryEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface DiaryEntryRepository extends JpaRepository<DiaryEntry, Long> {

    /**
     * Entries for {@code userId} eaten in the half-open window
     * {@code [start, end)}, oldest first. Used by the day endpoint — the
     * client wants timeline order so it can group by {@code mealLabel}
     * without re-sorting.
     */
    @Query("""
            SELECT e FROM DiaryEntry e
            WHERE e.userId = :userId
              AND e.eatenAt >= :start
              AND e.eatenAt <  :end
            ORDER BY e.eatenAt ASC
            """)
    List<DiaryEntry> findByUserInRange(@Param("userId") Long userId,
                                       @Param("start") OffsetDateTime start,
                                       @Param("end") OffsetDateTime end);
}
