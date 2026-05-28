package com.example.athletehub.service;

import com.example.athletehub.dto.UpdateProfileRequest;
import com.example.athletehub.dto.UserDto;
import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.enums.Role;
import com.example.athletehub.exception.ResourceNotFoundException;
import com.example.athletehub.model.User;
import com.example.athletehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User-scoped operations beyond auth. AH-016 covers partial profile updates
 * and the role-switch endpoint (which grants a role on first use — there's no
 * separate "become a coach" upgrade flow in MVP).
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserDto getByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.RESOURCE_NOT_FOUND));
        return UserDto.from(user);
    }

    /**
     * Apply only the fields the caller actually sent. Null fields are left
     * unchanged — this is a true partial update, not a put-replace.
     */
    @Transactional
    public UserDto updateProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.RESOURCE_NOT_FOUND));

        if (request.getFullName() != null) user.setFullName(request.getFullName().trim());
        if (request.getBio() != null)       user.setBio(request.getBio());
        if (request.getAge() != null)       user.setAge(request.getAge());
        if (request.getHeightCm() != null)  user.setHeightCm(request.getHeightCm());
        if (request.getAvatarHue() != null) user.setAvatarHue(request.getAvatarHue());

        return UserDto.from(userRepository.save(user));
    }

    /**
     * Switch the user into {@code role}: grants the role if they don't have
     * it yet (becoming a coach is just switching to coach mode for the first
     * time), then returns the updated profile. The "active" role on the
     * client is a UI concern — every role the user holds remains valid.
     */
    @Transactional
    public UserDto switchRole(String email, Role role) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.RESOURCE_NOT_FOUND));

        if (user.getRoles().add(role)) {
            userRepository.save(user);
        }
        return UserDto.from(user);
    }
}
