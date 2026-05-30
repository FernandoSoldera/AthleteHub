package com.example.athletehub.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * One message in a conversation. {@code attachmentMediaId} is a soft int8
 * with no FK at MVP — {@code media_assets} lands in AH-092 and the FK
 * can be bolted on then (same pattern used for assignment ref_type /
 * ref_id). Hard-deletes are allowed (no soft-delete column); deleting a
 * sender CASCADEs through to their messages, which matches the data-
 * model spec (a deleted account's messages are scrubbed).
 */
@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(nullable = false)
    private String body;

    @Column(name = "attachment_media_id")
    private Long attachmentMediaId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
