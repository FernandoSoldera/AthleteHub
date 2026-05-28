package com.example.athletehub.repository;

import com.example.athletehub.enums.OAuthProvider;
import com.example.athletehub.model.OAuthAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, Long> {

    Optional<OAuthAccount> findByProviderAndProviderUid(OAuthProvider provider, String providerUid);
}
