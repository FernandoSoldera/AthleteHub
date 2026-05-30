package com.example.athletehub.repository;

import com.example.athletehub.model.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** Latest message in a thread (for inbox previews + last-message lookup). */
    Optional<Message> findFirstByConversationIdOrderByIdDesc(Long conversationId);

    /** Count messages in a thread that arrived after the read pointer. */
    @Query("""
            SELECT COUNT(m) FROM Message m
            WHERE m.conversationId = :conversationId
              AND (:lastReadId IS NULL OR m.id > :lastReadId)
              AND m.senderId <> :viewerId
            """)
    long countUnread(@Param("conversationId") Long conversationId,
                     @Param("viewerId") Long viewerId,
                     @Param("lastReadId") Long lastReadId);

    // ── thread paging ────────────────────────────────────────────────────
    //
    // Newest-first by id (id is monotonic and matches creation order). The
    // cursor-split mirrors the assignments repo (AH-074) so a null cursor
    // doesn't trip PG type inference.

    /** First-page thread listing (no cursor) — newest first. */
    @Query("""
            SELECT m FROM Message m
            WHERE m.conversationId = :conversationId
            ORDER BY m.id DESC
            """)
    List<Message> findFirstPage(@Param("conversationId") Long conversationId,
                                Pageable pageable);

    /** Cursor-paginated thread listing — rows strictly older than the cursor id. */
    @Query("""
            SELECT m FROM Message m
            WHERE m.conversationId = :conversationId
              AND m.id < :cursor
            ORDER BY m.id DESC
            """)
    List<Message> findBefore(@Param("conversationId") Long conversationId,
                             @Param("cursor") Long cursor,
                             Pageable pageable);
}
