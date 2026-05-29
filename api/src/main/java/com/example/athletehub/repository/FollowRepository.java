package com.example.athletehub.repository;

import com.example.athletehub.model.Follow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    Optional<Follow> findByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    @Modifying
    @Query("DELETE FROM Follow f WHERE f.followerId = :followerId AND f.followeeId = :followeeId")
    int deleteByFollowerIdAndFolloweeId(@Param("followerId") Long followerId,
                                        @Param("followeeId") Long followeeId);

    /** Followers of {@code userId}, cursor-paginated on {@code id < beforeId}. */
    @Query("""
            SELECT f FROM Follow f
            WHERE f.followeeId = :userId
              AND (:beforeId IS NULL OR f.id < :beforeId)
            ORDER BY f.id DESC
            """)
    List<Follow> findFollowersPage(@Param("userId") Long userId,
                                   @Param("beforeId") Long beforeId,
                                   Pageable pageable);

    /** People {@code userId} follows, cursor-paginated on {@code id < beforeId}. */
    @Query("""
            SELECT f FROM Follow f
            WHERE f.followerId = :userId
              AND (:beforeId IS NULL OR f.id < :beforeId)
            ORDER BY f.id DESC
            """)
    List<Follow> findFollowingPage(@Param("userId") Long userId,
                                   @Param("beforeId") Long beforeId,
                                   Pageable pageable);
}
