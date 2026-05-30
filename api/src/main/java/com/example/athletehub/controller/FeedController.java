package com.example.athletehub.controller;

import com.example.athletehub.dto.CursorPage;
import com.example.athletehub.dto.FeedItemDto;
import com.example.athletehub.security.UserPrincipal;
import com.example.athletehub.service.FeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Feed read endpoints (AH-062). The write paths live elsewhere
 * ({@code PostController} for create/delete, {@code LikeController} +
 * {@code CommentController} when AH-063 lands).
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;

    /**
     * Home timeline — viewer's own posts (any visibility) plus their
     * followees' public / followers posts. Optional comma-separated
     * {@code type} filter (e.g. {@code ?type=workout,run}).
     */
    @GetMapping("/feed")
    public CursorPage<FeedItemDto> getHomeFeed(
            Authentication authentication,
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "type", required = false) String type) {
        return feedService.getHomeFeed(currentUserId(authentication), cursor, clampLimit(limit), type);
    }

    /**
     * Profile feed — posts by {@code handle}, visibility-filtered to the
     * viewer-author relationship (self → all; follower → public +
     * followers; stranger → public).
     */
    @GetMapping("/users/{handle}/posts")
    public CursorPage<FeedItemDto> getProfileFeed(
            Authentication authentication,
            @PathVariable("handle") String handle,
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return feedService.getProfileFeed(currentUserId(authentication), handle, cursor, clampLimit(limit));
    }

    private Long currentUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal up) return up.getId();
        throw new IllegalStateException("Unexpected principal: " + principal.getClass());
    }

    private int clampLimit(int requested) {
        if (requested < 1) return 1;
        if (requested > 100) return 100;
        return requested;
    }
}
