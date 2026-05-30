package com.example.athletehub.repository;

import com.example.athletehub.model.PostLike;
import com.example.athletehub.model.PostLikeKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PostLikeRepository extends JpaRepository<PostLike, PostLikeKey> {

    /**
     * Of the given {@code postIds}, which ones did {@code viewerId} like?
     * Used by AH-062 to hydrate the {@code iLiked} flag on a feed page in
     * one round-trip instead of N point lookups.
     */
    @Query("""
            SELECT pl.postId FROM PostLike pl
            WHERE pl.userId = :viewerId
              AND pl.postId IN :postIds
            """)
    List<Long> findLikedPostIds(@Param("viewerId") Long viewerId,
                                @Param("postIds") Collection<Long> postIds);
}
