package com.example.athletehub.repository;

import com.example.athletehub.model.Favorite;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    /**
     * The user's favorites newest-first, cursor-paginated on {@code id DESC}.
     * Same {@code limit + 1} trick as elsewhere in the codebase.
     */
    @Query("""
            SELECT f FROM Favorite f
            WHERE f.userId = :userId
              AND (:beforeId IS NULL OR f.id < :beforeId)
            ORDER BY f.id DESC
            """)
    List<Favorite> findRecent(@Param("userId") Long userId,
                              @Param("beforeId") Long beforeId,
                              Pageable pageable);

    /**
     * Used by the find-or-insert {@code POST /api/diet/favorites} path so a
     * second favourite of the same food returns the existing row instead of
     * tripping the UNIQUE constraint.
     */
    Optional<Favorite> findByUserIdAndFoodId(Long userId, Long foodId);

    /**
     * Idempotent delete by natural key — the API contract is "favourite is
     * gone after this call", so deleting twice is fine. Returns the row
     * count so the caller can tell "actually removed" from "no-op".
     */
    @Modifying
    @Query("DELETE FROM Favorite f WHERE f.userId = :userId AND f.foodId = :foodId")
    int deleteByUserIdAndFoodId(@Param("userId") Long userId,
                                @Param("foodId") Long foodId);
}
