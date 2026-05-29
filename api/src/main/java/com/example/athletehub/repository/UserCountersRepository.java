package com.example.athletehub.repository;

import com.example.athletehub.model.UserCounters;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserCountersRepository extends JpaRepository<UserCounters, Long> {

    @Modifying
    @Query("UPDATE UserCounters c SET c.followers = c.followers + :delta WHERE c.userId = :userId")
    int adjustFollowers(@Param("userId") Long userId, @Param("delta") int delta);

    @Modifying
    @Query("UPDATE UserCounters c SET c.following = c.following + :delta WHERE c.userId = :userId")
    int adjustFollowing(@Param("userId") Long userId, @Param("delta") int delta);
}
