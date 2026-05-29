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
import com.example.athletehub.model.UserCounters;
import com.example.athletehub.repository.UserCountersRepository;
import com.example.athletehub.repository.UserRepository;
import com.example.athletehub.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Identity use cases. AH-011 register, AH-012 login + JWT, AH-013 refresh +
 * logout, AH-014 password reset. OAuth (AH-015) follows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final UserCountersRepository userCountersRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;
    private final EmailService emailService;

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
                .sex(req.getSex())  // null when caller omitted it
                .roles(roles)
                .build();

        User saved = userRepository.save(user);
        userCountersRepository.save(UserCounters.builder().userId(saved.getId()).build());
        return UserDto.from(saved);
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
     * Rotate the presented refresh token (see {@link RefreshTokenService#rotate}).
     *
     * <p>{@code noRollbackFor} must be set on this <em>outer</em> transaction
     * because the joined inner tx has its rollback rules ignored under default
     * (REQUIRED) propagation. Without this, the reuse-detection revocations
     * inside rotate would be rolled back.
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

    /**
     * Start a password-reset flow. The response is identical whether the email
     * exists or not (no account enumeration). If the email exists, a code is
     * issued and emailed; otherwise we do nothing. Mail-send errors are logged
     * and swallowed so they don't leak account existence via timing or status.
     */
    public void forgotPassword(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        userRepository.findByEmail(normalized).ifPresent(user -> {
            try {
                String code = passwordResetService.issueCodeForUser(user);
                emailService.sendPasswordResetCode(normalized, code);
            } catch (Exception ex) {
                log.warn("Failed to deliver password-reset email for {}", normalized, ex);
            }
        });
    }

    /**
     * Consume a reset code and set a new password. The code is single-use:
     * a second call with the same code fails.
     */
    @Transactional
    public void resetPassword(String code, String newPassword) {
        User user = passwordResetService.consumeCode(code);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
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
