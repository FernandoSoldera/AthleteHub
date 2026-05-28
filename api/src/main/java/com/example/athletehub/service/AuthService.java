package com.example.athletehub.service;

import com.example.athletehub.dto.SignupRequest;
import com.example.athletehub.dto.UserDto;
import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.enums.Role;
import com.example.athletehub.exception.ConflictException;
import com.example.athletehub.model.User;
import com.example.athletehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Identity use cases. AH-011 covers email/password registration; login + JWT
 * issuance, refresh rotation, password reset and OAuth land in AH-012..015.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Register a new athlete account. Email and handle are normalized to
     * lowercase (uniqueness is case-insensitive without depending on the
     * citext extension). Returns the persisted user as a DTO.
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

        User saved = userRepository.save(user);
        return UserDto.from(saved);
    }
}
