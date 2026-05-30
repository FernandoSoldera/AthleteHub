package com.example.athletehub.controller;

import com.example.athletehub.security.UserPrincipal;
import com.example.athletehub.service.InteractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Comment-scoped operations (AH-063). Lives on its own route so the
 * comment id namespace doesn't collide with the post id namespace under
 * {@code /api/posts}.
 */
@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final InteractionService interactionService;

    @DeleteMapping("/{id:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication,
                       @PathVariable("id") Long id) {
        interactionService.deleteComment(currentUserId(authentication), id);
    }

    private Long currentUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal up) return up.getId();
        throw new IllegalStateException("Unexpected principal: " + principal.getClass());
    }
}
