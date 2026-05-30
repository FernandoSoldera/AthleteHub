package com.example.athletehub.controller;

import com.example.athletehub.dto.ConversationDto;
import com.example.athletehub.dto.CursorPage;
import com.example.athletehub.dto.MessageDto;
import com.example.athletehub.dto.SendMessageRequest;
import com.example.athletehub.security.UserPrincipal;
import com.example.athletehub.service.MessagingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * AH-081 — messaging surfaces. Five routes:
 *
 * <ul>
 *   <li>{@code GET /api/conversations?cursor=&limit=} — inbox list with
 *       hydrated peer + unread count.</li>
 *   <li>{@code GET /api/conversations/{id}/messages?cursor=&limit=} —
 *       paginate one thread newest-first.</li>
 *   <li>{@code POST /api/conversations/{id}/messages} — send a message;
 *       bumps {@code last_message_at}/{@code last_message_preview}.</li>
 *   <li>{@code POST /api/conversations/{id}/read} — advance read pointer
 *       to the latest message id.</li>
 *   <li>{@code POST /api/me/coach-athletes/{id}/conversation} — open the
 *       (lazily-created) thread for a relationship; returns the
 *       conversation DTO so the client can navigate straight into it.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MessagingController {

    private final MessagingService messagingService;

    @GetMapping("/conversations")
    public CursorPage<ConversationDto> inbox(
            Authentication authentication,
            @RequestParam(value = "cursor", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime cursor,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return messagingService.listInbox(currentUserId(authentication), cursor, clampLimit(limit));
    }

    @GetMapping("/conversations/{id:\\d+}/messages")
    public CursorPage<MessageDto> messages(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "limit", defaultValue = "30") int limit) {
        return messagingService.listMessages(
                currentUserId(authentication), id, cursor, clampLimit(limit));
    }

    @PostMapping("/conversations/{id:\\d+}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDto send(Authentication authentication,
                           @PathVariable("id") Long id,
                           @Valid @RequestBody SendMessageRequest request) {
        return messagingService.sendMessage(currentUserId(authentication), id, request);
    }

    @PostMapping("/conversations/{id:\\d+}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(Authentication authentication, @PathVariable("id") Long id) {
        messagingService.markRead(currentUserId(authentication), id);
    }

    @PostMapping("/me/coach-athletes/{relationshipId:\\d+}/conversation")
    public ConversationDto openForRelationship(Authentication authentication,
                                               @PathVariable("relationshipId") Long relationshipId) {
        return messagingService.openForRelationship(currentUserId(authentication), relationshipId);
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
