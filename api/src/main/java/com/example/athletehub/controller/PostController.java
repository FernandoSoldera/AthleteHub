package com.example.athletehub.controller;

import com.example.athletehub.dto.CommentDto;
import com.example.athletehub.dto.CreateCommentRequest;
import com.example.athletehub.dto.CreateManualPostRequest;
import com.example.athletehub.dto.CursorPage;
import com.example.athletehub.dto.PostDto;
import com.example.athletehub.security.UserPrincipal;
import com.example.athletehub.service.InteractionService;
import com.example.athletehub.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual post create + author-scoped delete (AH-061) +
 * like / comment endpoints (AH-063).
 *
 * <p>Visibility for the AH-063 paths is delegated to
 * {@link PostService#loadVisible} so the gate is enforced uniformly:
 * own posts always work; public posts work for anyone; followers posts
 * only for followers; private posts only for the author. Comment delete
 * lives on a separate route ({@code /api/comments/{id}}) so the
 * comment-id namespace doesn't collide with the post-id one.
 */
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final InteractionService interactionService;

    // ── AH-061 post CRUD ──────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostDto create(Authentication authentication,
                          @Valid @RequestBody(required = false) CreateManualPostRequest request) {
        return postService.publishManual(
                currentUserId(authentication),
                request == null ? new CreateManualPostRequest() : request);
    }

    @DeleteMapping("/{id:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication,
                       @PathVariable("id") Long id) {
        postService.softDelete(currentUserId(authentication), id);
    }

    // ── AH-063 likes ──────────────────────────────────────────────────────

    @PostMapping("/{id:\\d+}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void like(Authentication authentication,
                     @PathVariable("id") Long id) {
        interactionService.like(currentUserId(authentication), id);
    }

    @DeleteMapping("/{id:\\d+}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlike(Authentication authentication,
                       @PathVariable("id") Long id) {
        interactionService.unlike(currentUserId(authentication), id);
    }

    // ── AH-063 comments ───────────────────────────────────────────────────

    @PostMapping("/{id:\\d+}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentDto addComment(Authentication authentication,
                                 @PathVariable("id") Long id,
                                 @Valid @RequestBody CreateCommentRequest request) {
        return interactionService.addComment(currentUserId(authentication), id, request);
    }

    @GetMapping("/{id:\\d+}/comments")
    public CursorPage<CommentDto> listComments(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return interactionService.listComments(
                currentUserId(authentication), id, cursor, clampLimit(limit));
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
