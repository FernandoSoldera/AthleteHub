package com.example.athletehub.controller;

import com.example.athletehub.dto.CreateManualPostRequest;
import com.example.athletehub.dto.PostDto;
import com.example.athletehub.security.UserPrincipal;
import com.example.athletehub.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual post create + author-scoped delete (AH-061).
 *
 * <p>Auto-posts from workout / cardio / evaluation are triggered
 * internally from the originating service — there's no public endpoint
 * for those. The feed read endpoints (timeline, profile feed) land in
 * AH-062.
 */
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostDto create(Authentication authentication,
                          @Valid @RequestBody(required = false) CreateManualPostRequest request) {
        return postService.publishManual(
                currentUserId(authentication),
                request == null ? new CreateManualPostRequest() : request);
    }

    /** Soft-delete by id. 404 on someone else's post (no disclosure). */
    @DeleteMapping("/{id:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication,
                       @PathVariable("id") Long id) {
        postService.softDelete(currentUserId(authentication), id);
    }

    private Long currentUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal up) return up.getId();
        throw new IllegalStateException("Unexpected principal: " + principal.getClass());
    }
}
