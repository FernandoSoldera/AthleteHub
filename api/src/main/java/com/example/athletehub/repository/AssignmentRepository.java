package com.example.athletehub.repository;

import com.example.athletehub.model.Assignment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    // ── per-relationship listing ──────────────────────────────────────────
    //
    // Same null-param query-split trick as the exercise catalog (AH-031):
    // passing a null LocalDate into PostgreSQL via JPQL trips type
    // inference (PG error 42P18 — "could not determine data type of
    // parameter $4"). Keeping the date filter on its own query means we
    // never pass a null where a typed value is required.

    /** Per-relationship listing without a scheduled-date filter. */
    @Query("""
            SELECT a FROM Assignment a
            WHERE a.coachAthleteId = :relationshipId
              AND (:status IS NULL OR a.status = :status)
              AND (:cursor IS NULL OR a.id < :cursor)
            ORDER BY a.id DESC
            """)
    List<Assignment> findForRelationship(@Param("relationshipId") Long relationshipId,
                                         @Param("status") String status,
                                         @Param("cursor") Long cursor,
                                         Pageable pageable);

    /** Per-relationship listing with the scheduled-date filter applied. */
    @Query("""
            SELECT a FROM Assignment a
            WHERE a.coachAthleteId = :relationshipId
              AND (:status IS NULL OR a.status = :status)
              AND a.scheduledFor = :scheduledOn
              AND (:cursor IS NULL OR a.id < :cursor)
            ORDER BY a.id DESC
            """)
    List<Assignment> findForRelationshipOnDate(@Param("relationshipId") Long relationshipId,
                                               @Param("status") String status,
                                               @Param("scheduledOn") LocalDate scheduledOn,
                                               @Param("cursor") Long cursor,
                                               Pageable pageable);

    // ── multi-relationship listing (athlete self-view) ───────────────────

    /** Athlete's assignments across multiple relationships, no date filter. */
    @Query("""
            SELECT a FROM Assignment a
            WHERE a.coachAthleteId IN :relationshipIds
              AND (:status IS NULL OR a.status = :status)
              AND (:cursor IS NULL OR a.id < :cursor)
            ORDER BY a.id DESC
            """)
    List<Assignment> findForRelationships(@Param("relationshipIds") Collection<Long> relationshipIds,
                                          @Param("status") String status,
                                          @Param("cursor") Long cursor,
                                          Pageable pageable);

    /** Athlete's assignments across multiple relationships, with date filter. */
    @Query("""
            SELECT a FROM Assignment a
            WHERE a.coachAthleteId IN :relationshipIds
              AND (:status IS NULL OR a.status = :status)
              AND a.scheduledFor = :scheduledOn
              AND (:cursor IS NULL OR a.id < :cursor)
            ORDER BY a.id DESC
            """)
    List<Assignment> findForRelationshipsOnDate(@Param("relationshipIds") Collection<Long> relationshipIds,
                                                @Param("status") String status,
                                                @Param("scheduledOn") LocalDate scheduledOn,
                                                @Param("cursor") Long cursor,
                                                Pageable pageable);
}
