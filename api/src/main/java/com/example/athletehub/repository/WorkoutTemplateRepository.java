package com.example.athletehub.repository;

import com.example.athletehub.model.WorkoutTemplate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WorkoutTemplateRepository extends JpaRepository<WorkoutTemplate, Long> {

    /**
     * Templates owned by {@code userId} that are scheduled on
     * {@code dayOfWeek} (ISO Mon=1 … Sun=7). Sorted by schedule row id so
     * if a user happens to have two templates pinned to the same weekday
     * (which the schedule UNIQUE-per-template constraint allows), the
     * caller can pick one deterministically — for now we just take the
     * first.
     */
    @Query("""
            SELECT t FROM WorkoutTemplate t
            JOIN TemplateSchedule ts ON ts.templateId = t.id
            WHERE t.ownerId = :userId
              AND ts.dayOfWeek = :dayOfWeek
            ORDER BY ts.id ASC
            """)
    List<WorkoutTemplate> findScheduledFor(@Param("userId") Long userId,
                                           @Param("dayOfWeek") short dayOfWeek,
                                           Pageable pageable);
}
