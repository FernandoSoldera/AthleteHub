package com.example.athletehub.service;

import com.example.athletehub.dto.CursorPage;
import com.example.athletehub.dto.PublicUserDto;
import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.exception.BadRequestException;
import com.example.athletehub.exception.ResourceNotFoundException;
import com.example.athletehub.model.Follow;
import com.example.athletehub.model.User;
import com.example.athletehub.repository.FollowRepository;
import com.example.athletehub.repository.UserCountersRepository;
import com.example.athletehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Follow/unfollow + paginated list reads. Follow + unfollow are idempotent —
 * issuing them twice yields the same final state and counters move at most
 * once per call, never below zero in practice (a follow row exists only when
 * the counters reflect it, and both move in the same transaction).
 */
@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepository;
    private final UserCountersRepository userCountersRepository;
    private final UserRepository userRepository;

    @Transactional
    public void follow(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw new BadRequestException("Cannot follow yourself");
        }
        if (!userRepository.existsById(followeeId)) {
            throw new ResourceNotFoundException(MessageCode.RESOURCE_NOT_FOUND);
        }
        if (followRepository.findByFollowerIdAndFolloweeId(followerId, followeeId).isPresent()) {
            return; // idempotent — already following
        }
        followRepository.save(Follow.builder()
                .followerId(followerId)
                .followeeId(followeeId)
                .build());
        userCountersRepository.adjustFollowers(followeeId, 1);
        userCountersRepository.adjustFollowing(followerId, 1);
    }

    @Transactional
    public void unfollow(Long followerId, Long followeeId) {
        int deleted = followRepository.deleteByFollowerIdAndFolloweeId(followerId, followeeId);
        if (deleted > 0) {
            userCountersRepository.adjustFollowers(followeeId, -1);
            userCountersRepository.adjustFollowing(followerId, -1);
        }
    }

    @Transactional(readOnly = true)
    public CursorPage<PublicUserDto> listFollowers(Long userId, Long cursor, int limit) {
        // Fetch limit+1 so we can tell "more pages exist" from "exactly limit, no more".
        List<Follow> follows = followRepository.findFollowersPage(userId, cursor, PageRequest.of(0, limit + 1));
        return page(follows, Follow::getFollowerId, limit);
    }

    @Transactional(readOnly = true)
    public CursorPage<PublicUserDto> listFollowing(Long userId, Long cursor, int limit) {
        List<Follow> follows = followRepository.findFollowingPage(userId, cursor, PageRequest.of(0, limit + 1));
        return page(follows, Follow::getFolloweeId, limit);
    }

    private CursorPage<PublicUserDto> page(List<Follow> follows,
                                           Function<Follow, Long> userIdExtractor,
                                           int limit) {
        if (follows.isEmpty()) return CursorPage.of(List.of(), null);

        boolean hasMore = follows.size() > limit;
        List<Follow> visible = hasMore ? follows.subList(0, limit) : follows;

        List<Long> userIds = visible.stream().map(userIdExtractor).toList();
        Map<Long, User> byId = new HashMap<>();
        userRepository.findAllById(userIds).forEach(u -> byId.put(u.getId(), u));

        List<PublicUserDto> items = visible.stream()
                .map(userIdExtractor)
                .map(byId::get)
                .filter(u -> u != null)
                .map(PublicUserDto::from)
                .toList();

        String nextCursor = hasMore
                ? String.valueOf(visible.get(visible.size() - 1).getId())
                : null;
        return CursorPage.of(items, nextCursor);
    }
}
