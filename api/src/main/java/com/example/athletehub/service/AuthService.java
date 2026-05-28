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
 * username/password login + JWT issuance. Refresh rotation (AH-013), password
 * reset (AH-014) and OAuth (AH-015) follow.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

    /**
     * Register a new athlete account. Email and handle are normalized to
     * lowercase (case-insensitive uniqueness without depending on citext).
     *
     * @throws ConflictException with {@link MessageCode#EMAIL_ALREADY_REGISTERED}
     *         or {@link MessageCode#HANDLE_ALREADY_TAKEN} if either is taken.
     */
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

    /**
     * Authenticate with email + password and issue an access token (JWT) plus a
     * refresh token. The same {@link BadCredentialsException} is thrown for an
     * unknown email or a wrong password — no user enumeration.
     */
    @Transactional
    public AuthResponse login(LoginRequest req, String deviceInfo) {
        String email = req.getEmail().trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

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
