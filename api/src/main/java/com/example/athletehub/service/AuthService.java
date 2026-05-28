package com.example.athletehub.service;

import com.example.athletehub.dto.AuthResponse;
import com.example.athletehub.dto.LoginRequest;
import com.example.athletehub.dto.SignupRequest;
import com.example.athletehub.dto.UserDto;
import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.enums.Role;
import com.example.athletehub.exception.ConflictException;
import com.example.athletehub.exception.InvalidCredentialsException;
import com.example.athletehub.model.User;
import com.example.athletehub.repository.UserRepository;
import com.example.athletehub.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Identity use cases. AH-011 covers email/password registration; AH-012 adds
 * login + JWT issuance; AH-013 adds refresh-token rotation + logout. Password
 * reset (AH-014) and OAuth (AH-015) follow.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public UserDto register(SignupRequest req) {
        String email = req.getEmail().trim().toLowerCase(Locale.ROOT);
        String handle = req.getHandle().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException(MessageCode.EMAIL_ALREADY_REGISTERED);
        }
        if (userRepository.existsByHandle(handle)) {
            throw new ConflictException(MessageCode.HANDLE_ALREADY_TAKEN);
        }

        Set<Role> roles = new HashSet<>();
        roles.add(Role.ATHLETE);

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .fullName(req.getFullName().trim())
                .handle(handle)
                .roles(roles)
                .build();

        return UserDto.from(userRepository.save(user));
    }

    @Transactional
    public AuthResponse login(LoginRequest req, String deviceInfo) {
        String email = req.getEmail().trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return issueTokens(user, deviceInfo);
    }

    /**
     * Rotate the presented refresh token. Issues a fresh access + refresh
     * token pair tied to the same user; the presented refresh token is
     * revoked. See {@link RefreshTokenService#rotate} for reuse-detection
     * semantics.
     *
     * <p>{@code noRollbackFor} must be set on this <em>outer</em> transaction
     * because rotate's joined tx has its rollback rules ignored under default
     * (REQUIRED) propagation. Without this, the reuse-detection revocations
     * inside rotate would be rolled back when it throws.
     */
    @Transactional(noRollbackFor = com.example.athletehub.exception.InvalidRefreshTokenException.class)
    public AuthResponse refresh(String plainRefreshToken, String deviceInfo) {
        RefreshTokenService.IssuedRefreshToken rotated =
                refreshTokenService.rotate(plainRefreshToken, deviceInfo);
        User user = rotated.entity().getUser();
        String accessToken = jwtUtil.generateAccessToken(user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rotated.plainValue())
                .accessTokenExpiresIn(jwtUtil.getAccessTokenExpirationMs())
                .user(UserDto.from(user))
                .build();
    }

    /** Idempotent logout — revokes the presented refresh token if it exists. */
    @Transactional
    public void logout(String plainRefreshToken) {
        refreshTokenService.revoke(plainRefreshToken);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

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
