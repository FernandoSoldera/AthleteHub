package com.example.athletehub.repository;

import com.example.athletehub.model.RefreshToken;
import com.example.athletehub.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Used during reuse-detection to revoke every active token for a user
     *  (e.g. when a revoked token is re-presented). */
    List<RefreshToken> findAllByUserAndRevokedAtIsNull(User user);
}
