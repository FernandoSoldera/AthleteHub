package com.example.athletehub.repository;

import com.example.athletehub.model.ConversationParticipant;
import com.example.athletehub.model.ConversationParticipantKey;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ConversationParticipantRepository
        extends JpaRepository<ConversationParticipant, ConversationParticipantKey> {

    Optional<ConversationParticipant> findByConversationIdAndUserId(Long conversationId,
                                                                    Long userId);

    /** Participants of one conversation — typically two rows at MVP. */
    List<ConversationParticipant> findByConversationId(Long conversationId);

    // ── inbox listing ────────────────────────────────────────────────────
    //
    // Two queries split by the cursor presence — same null-param query-
    // split trick used by the assignments repo (AH-074): passing a null
    // OffsetDateTime into the strict-less filter would trip PG type
    // inference. Order by lastMessageAt DESC NULLS LAST so brand-new
    // empty threads sort below active ones; cursor by lastMessageAt
    // alone trades a sub-millisecond duplicate-row edge case (two
    // threads with identical lastMessageAt) for query simplicity.

    /** First-page inbox listing (no cursor). */
    @Query("""
            SELECT p.conversationId FROM ConversationParticipant p
            JOIN Conversation c ON c.id = p.conversationId
            WHERE p.userId = :userId
            ORDER BY c.lastMessageAt DESC NULLS LAST, c.id DESC
            """)
    List<Long> findInboxConversationIds(@Param("userId") Long userId,
                                        Pageable pageable);

    /** Cursor-paginated inbox listing — rows strictly older than the cursor. */
    @Query("""
            SELECT p.conversationId FROM ConversationParticipant p
            JOIN Conversation c ON c.id = p.conversationId
            WHERE p.userId = :userId
              AND c.lastMessageAt IS NOT NULL
              AND c.lastMessageAt < :cursor
            ORDER BY c.lastMessageAt DESC, c.id DESC
            """)
    List<Long> findInboxConversationIdsBefore(@Param("userId") Long userId,
                                              @Param("cursor") OffsetDateTime cursor,
                                              Pageable pageable);
}
