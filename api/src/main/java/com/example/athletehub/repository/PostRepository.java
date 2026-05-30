package com.example.athletehub.repository;

import com.example.athletehub.model.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    // ── counter maintenance (AH-063) ──────────────────────────────────────

    /** Move {@code like_count} by {@code delta}. Schema CHECK keeps the
     *  value non-negative; callers only decrement when a row actually
     *  existed, so the constraint never fires in normal use. */
    @Modifying
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + :delta WHERE p.id = :postId")
    int adjustLikeCount(@Param("postId") Long postId, @Param("delta") int delta);

    /** Same shape, for the {@code comment_count} column. */
    @Modifying
    @Query("UPDATE Post p SET p.commentCount = p.commentCount + :delta WHERE p.id = :postId")
    int adjustCommentCount(@Param("postId") Long postId, @Param("delta") int delta);

    // ── home feed (AH-062) ────────────────────────────────────────────────

    /**
     * Home timeline for {@code viewerId}: their own posts (any visibility,
     * including private) plus their followees' posts where visibility is
     * not {@code private}. Soft-deleted rows excluded.
     *
     * <p>Cursor pagination on {@code id DESC} (surrogate id is monotonic
     * and roughly time-ordered — newer posts have higher ids — so it
     * matches the {@code created_at DESC} the feed wants in practice).
     * The service fetches {@code limit + 1} and uses the extra row to
     * tell "more pages" from "exactly limit, no more".
     */
    @Query("""
            SELECT p FROM Post p
            WHERE p.deletedAt IS NULL
              AND (p.authorId = :viewerId
                   OR p.authorId IN (
                       SELECT f.followeeId FROM Follow f WHERE f.followerId = :viewerId))
              AND (p.authorId = :viewerId OR p.visibility <> 'private')
              AND (:cursor IS NULL OR p.id < :cursor)
            ORDER BY p.id DESC
            """)
    List<Post> findHomeFeed(@Param("viewerId") Long viewerId,
                            @Param("cursor") Long cursor,
                            Pageable pageable);

    /**
     * Same visibility rule as {@link #findHomeFeed} with a post-type
     * filter. Split from the unfiltered branch so JPA's empty-IN-list
     * handling doesn't bite (same call as the exercises catalog).
     */
    @Query("""
            SELECT p FROM Post p
            WHERE p.deletedAt IS NULL
              AND (p.authorId = :viewerId
                   OR p.authorId IN (
                       SELECT f.followeeId FROM Follow f WHERE f.followerId = :viewerId))
              AND (p.authorId = :viewerId OR p.visibility <> 'private')
              AND p.type IN :types
              AND (:cursor IS NULL OR p.id < :cursor)
            ORDER BY p.id DESC
            """)
    List<Post> findHomeFeedByTypes(@Param("viewerId") Long viewerId,
                                   @Param("types") Collection<String> types,
                                   @Param("cursor") Long cursor,
                                   Pageable pageable);

    // ── profile feed (AH-062) ─────────────────────────────────────────────

    /**
     * Profile feed for one author. The service derives
     * {@code allowedVisibilities} from the viewer / author relationship:
     * self → {public, followers, private}; follower → {public, followers};
     * stranger → {public}. Soft-deleted rows excluded.
     */
    @Query("""
            SELECT p FROM Post p
            WHERE p.deletedAt IS NULL
              AND p.authorId = :authorId
              AND p.visibility IN :allowedVisibilities
              AND (:cursor IS NULL OR p.id < :cursor)
            ORDER BY p.id DESC
            """)
    List<Post> findProfileFeed(@Param("authorId") Long authorId,
                               @Param("allowedVisibilities") Collection<String> allowedVisibilities,
                               @Param("cursor") Long cursor,
                               Pageable pageable);
}
