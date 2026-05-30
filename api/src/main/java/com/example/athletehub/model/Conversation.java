package com.example.athletehub.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * One messaging thread row — the inbox-list source. {@code coachAthleteId}
 * tags the thread back to its coach-athlete relationship (1:1 per
 * relationship via a partial-unique index) and is nullable so a future
 * non-coaching DM doesn't need a schema change; for MVP every conversation
 * has a relationship tag.
 *
 * <p>{@code lastMessageAt} + {@code lastMessagePreview} are denormalised
 * hot fields updated by the send-message path so the inbox list is one
 * indexed read per thread, not a correlated subquery into {@code messages}.
 * The preview is capped at 280 chars by a CHECK constraint; the service
 * truncates on write.
 */
@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coach_athlete_id")
    private Long coachAthleteId;

    @Column(name = "last_message_at")
    private OffsetDateTime lastMessageAt;

    @Column(name = "last_message_preview")
    private String lastMessagePreview;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
