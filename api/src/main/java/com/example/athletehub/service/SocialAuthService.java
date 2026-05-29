package com.example.athletehub.service;

import com.example.athletehub.dto.AuthResponse;
import com.example.athletehub.dto.UserDto;
import com.example.athletehub.enums.OAuthProvider;
import com.example.athletehub.enums.Role;
import com.example.athletehub.model.OAuthAccount;
import com.example.athletehub.model.User;
import com.example.athletehub.model.UserCounters;
import com.example.athletehub.repository.OAuthAccountRepository;
import com.example.athletehub.repository.UserCountersRepository;
import com.example.athletehub.repository.UserRepository;
import com.example.athletehub.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Mobile-app driven social login (AH-015). The app obtains an ID token from
 * Google / Apple (native SDK) and posts it here. We verify the token, then:
 *
 * <ul>
 *   <li>existing oauth_account → load that user, issue tokens;</li>
 *   <li>known email but no oauth_account yet → link the existing user;</li>
 *   <li>unknown email → create a new user with role ATHLETE and a derived
 *       handle, then link.</li>
 * </ul>
 *
 * <p>Email-based linking is safe because Google + Apple both verify their
 * users' emails; the ID token carries that verified email.
 */
@Service
@RequiredArgsConstructor
public class SocialAuthService {

    private final OAuthTokenVerifier verifier;
    private final UserRepository userRepository;
    private final UserCountersRepository userCountersRepository;
    private final OAuthAccountRepository oauthAccountRepository;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public AuthResponse loginWithProvider(OAuthProvider provider, String idToken, String deviceInfo) {
        OAuthTokenVerifier.OAuthIdentity identity = verifier.verify(provider, idToken);

        User user = oauthAccountRepository
                .findByProviderAndProviderUid(provider, identity.providerUid())
                .map(OAuthAccount::getUser)
                .orElseGet(() -> linkOrCreateUser(provider, identity));

        return issueTokens(user, deviceInfo);
    }

    private User linkOrCreateUser(OAuthProvider provider, OAuthTokenVerifier.OAuthIdentity identity) {
        String email = identity.email().trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(email).orElseGet(() -> createUser(email, identity));

        // Link the OAuth account to the user.
        OAuthAccount link = OAuthAccount.builder()
                .user(user)
                .provider(provider)
                .providerUid(identity.providerUid())
                .build();
        oauthAccountRepository.save(link);

        return user;
    }

    private User createUser(String email, OAuthTokenVerifier.OAuthIdentity identity) {
        Set<Role> roles = new HashSet<>();
        roles.add(Role.ATHLETE);

        User user = User.builder()
                .email(email)
                .passwordHash(null) // OAuth-only — no local password yet
                .fullName(deriveFullName(email, identity.displayName()))
                .handle(generateUniqueHandle(email))
                .roles(roles)
                .build();
        User saved = userRepository.save(user);
        userCountersRepository.save(UserCounters.builder().userId(saved.getId()).build());
        return saved;
    }

    /**
     * Derive a unique, valid handle from the email's local part. Falls back to
     * a {@code user_…} prefix when the cleaned local part is too short, and
     * appends a numeric suffix on collision.
     */
    private String generateUniqueHandle(String email) {
        String local = email.substring(0, email.indexOf('@'))
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._]", "");
        String base = local.length() >= 3 ? local : "user_" + local;
        String candidate = base;
        int suffix = 0;
        while (userRepository.existsByHandle(candidate)) {
            suffix++;
            candidate = base + "_" + suffix;
        }
        return candidate;
    }

    private static String deriveFullName(String email, String providerName) {
        if (providerName != null && !providerName.isBlank()) return providerName.trim();
        return email.substring(0, email.indexOf('@'));
    }

    private AuthResponse issueTokens(User user, String deviceInfo) {
        String accessToken = jwtUtil.generateAccessToken(user.getId());
        RefreshTokenService.IssuedRefreshToken refresh = refreshTokenService.issue(user, deviceInfo);
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refresh.plainValue())
                .accessTokenExpiresIn(jwtUtil.getAccessTokenExpirationMs())
                .user(UserDto.from(user))
                .build();
    }
}
