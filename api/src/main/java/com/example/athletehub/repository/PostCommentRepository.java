package com.example.athletehub.repository;

import com.example.athletehub.model.PostComment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    /**
     * Chronological thread for one post — oldest first, soft-deleted rows
     * excluded. Cursor pagination on {@code id ASC, id > beforeId} so
     * loading the next batch picks up exactly where the previous left off.
     */
    @Query("""
            SELECT c FROM PostComment c
            WHERE c.postId = :postId
              AND c.deletedAt IS NULL
              AND (:cursor IS NULL OR c.id > :cursor)
            ORDER BY c.id ASC
            """)
    List<PostComment> findThread(@Param("postId") Long postId,
                                 @Param("cursor") Long cursor,
                                 Pageable pageable);
}
