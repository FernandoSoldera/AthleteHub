package com.example.athletehub.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Membership + read-pointer row for a conversation. Composite PK on
 * {@code (conversation_id, user_id)} — the same join table doubles as
 * the "what threads is this user in?" index.
 *
 * <p>{@code lastReadMessageId} advances when the viewer opens the thread
 * (see AH-081 {@code POST /api/v1/conversations/{id}/read}); the unread
 * count is just {@code messages.id > lastReadMessageId}, a single index
 * hit on {@code idx_messages_conversation_created}. There's no FK from
 * {@code lastReadMessageId} → messages on purpose — a stale pointer
 * past the latest message is harmless (treated as zero unread).
 */
@Entity
@Table(name = "conversation_participants")
@IdClass(ConversationParticipantKey.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationParticipant {

    @Id
    @Column(name = "conversation_id")
    private Long conversationId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private OffsetDateTime joinedAt;

    @PrePersist
    void onCreate() {
        if (joinedAt == null) joinedAt = OffsetDateTime.now();
    }
}
