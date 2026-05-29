package com.example.athletehub.repository;

import com.example.athletehub.dto.SuggestedUserDto;
import com.example.athletehub.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByHandle(String handle);

    boolean existsByEmail(String email);

    boolean existsByHandle(String handle);

    /**
     * Free-text search across {@code fullName} and {@code handle}.
     * Cursor-paginated on {@code id < beforeId} (most recent first).
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.deletedAt IS NULL
              AND u.id <> :me
              AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(u.handle) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:beforeId IS NULL OR u.id < :beforeId)
            ORDER BY u.id DESC
            """)
    List<User> searchByNameOrHandle(@Param("me") Long me,
                                    @Param("q") String q,
                                    @Param("beforeId") Long beforeId,
                                    Pageable pageable);

    /**
     * Users I don't follow yet, with a mutual-follow count via a correlated
     * subquery: how many people I follow that this candidate also follows.
     */
    @Query("""
            SELECT new com.example.athletehub.dto.SuggestedUserDto(
                u.id, u.fullName, u.handle, u.avatarHue, u.bio,
                (SELECT COUNT(f1) FROM Follow f1
                 WHERE f1.followerId = :me
                   AND f1.followeeId IN (
                       SELECT f2.followeeId FROM Follow f2 WHERE f2.followerId = u.id))
            )
            FROM User u
            WHERE u.deletedAt IS NULL
              AND u.id <> :me
              AND u.id NOT IN (SELECT f.followeeId FROM Follow f WHERE f.followerId = :me)
              AND (:beforeId IS NULL OR u.id < :beforeId)
            ORDER BY u.id DESC
            """)
    List<SuggestedUserDto> findSuggestionsFor(@Param("me") Long me,
                                              @Param("beforeId") Long beforeId,
                                              Pageable pageable);
}
