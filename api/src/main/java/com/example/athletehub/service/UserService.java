package com.example.athletehub.service;

import com.example.athletehub.dto.CursorPage;
import com.example.athletehub.dto.PublicProfileResponse;
import com.example.athletehub.dto.PublicUserDto;
import com.example.athletehub.dto.SuggestedUserDto;
import com.example.athletehub.dto.UpdateProfileRequest;
import com.example.athletehub.dto.UserDto;
import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.enums.Role;
import com.example.athletehub.exception.ResourceNotFoundException;
import com.example.athletehub.model.User;
import com.example.athletehub.model.UserCounters;
import com.example.athletehub.repository.FollowRepository;
import com.example.athletehub.repository.UserCountersRepository;
import com.example.athletehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * User-scoped operations beyond auth: profile read/update, role switch
 * (AH-016), free-text search + suggestions (AH-022), and the public profile
 * aggregate by handle (AH-023).
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserCountersRepository userCountersRepository;
    private final FollowRepository followRepository;

    @Transactional(readOnly = true)
    public UserDto getByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.RESOURCE_NOT_FOUND));
        return UserDto.from(user);
    }

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

    @Transactional
    public UserDto switchRole(String email, Role role) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.RESOURCE_NOT_FOUND));

        if (user.getRoles().add(role)) {
            userRepository.save(user);
        }
        return UserDto.from(user);
    }

    // ── AH-022 — search + suggestions ─────────────────────────────────────

    @Transactional(readOnly = true)
    public CursorPage<PublicUserDto> searchUsers(Long me, String q, Long cursor, int limit) {
        if (q == null || q.trim().isEmpty()) return CursorPage.of(List.of(), null);
        List<User> users = userRepository.searchByNameOrHandle(
                me, q.trim(), cursor, PageRequest.of(0, limit + 1));
        boolean hasMore = users.size() > limit;
        List<User> visible = hasMore ? users.subList(0, limit) : users;
        List<PublicUserDto> items = visible.stream().map(PublicUserDto::from).toList();
        String nextCursor = hasMore
                ? String.valueOf(visible.get(visible.size() - 1).getId())
                : null;
        return CursorPage.of(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public CursorPage<SuggestedUserDto> suggestions(Long me, Long cursor, int limit) {
        List<SuggestedUserDto> suggestions = userRepository.findSuggestionsFor(
                me, cursor, PageRequest.of(0, limit + 1));
        boolean hasMore = suggestions.size() > limit;
        List<SuggestedUserDto> visible = hasMore ? suggestions.subList(0, limit) : suggestions;
        String nextCursor = hasMore
                ? String.valueOf(visible.get(visible.size() - 1).id())
                : null;
        return CursorPage.of(visible, nextCursor);
    }

    // ── AH-023 — public profile aggregate ─────────────────────────────────

    @Transactional(readOnly = true)
    public PublicProfileResponse getProfileByHandle(Long me, String handle) {
        User user = userRepository.findByHandle(handle.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.RESOURCE_NOT_FOUND));
        UserCounters counters = userCountersRepository.findById(user.getId())
                .orElseGet(() -> UserCounters.builder().userId(user.getId()).build());
        boolean iFollow = !me.equals(user.getId())
                && followRepository.findByFollowerIdAndFolloweeId(me, user.getId()).isPresent();
        return new PublicProfileResponse(
                PublicUserDto.from(user),
                counters.getFollowers(),
                counters.getFollowing(),
                iFollow);
    }
}
