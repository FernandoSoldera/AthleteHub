package com.example.athletehub.dto;

import java.time.OffsetDateTime;

/**
 * Inbox-list view of a conversation. {@code peer} is the *other* participant
 * (at MVP every conversation is 1:1 — coach↔athlete), hydrated so the inbox
 * can render an avatar + name without a second round-trip. {@code unreadCount}
 * is messages from the peer that arrived after the caller's
 * {@code lastReadMessageId} — never includes the caller's own sends.
 */
public record ConversationDto(
        Long id,
        Long coachAthleteId,
        OffsetDateTime lastMessageAt,
        String lastMessagePreview,
        long unreadCount,
        PublicUserDto peer
) {}
