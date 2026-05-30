package com.example.athletehub.controller;

import com.example.athletehub.dto.CoachInviteDto;
import com.example.athletehub.dto.CreateInviteRequest;
import com.example.athletehub.security.UserPrincipal;
import com.example.athletehub.service.CoachLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AH-071 — coach↔athlete invite + consent linking. Four routes:
 *
 * <ul>
 *   <li>{@code POST /api/coach/invites} — coach sends an invite by
 *       athlete handle. 201 on new row, 201 on revived {@code ended} row,
 *       409 on existing {@code pending}/{@code active} row.</li>
 *   <li>{@code GET /api/me/coach-invites} — athlete's pending inbox.</li>
 *   <li>{@code POST /api/me/coach-invites/{id}/accept} — flip to
 *       {@code active}.</li>
 *   <li>{@code POST /api/me/coach-invites/{id}/decline} — flip to
 *       {@code ended}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CoachInviteController {

    private final CoachLinkService coachLinkService;

    @PostMapping("/coach/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public CoachInviteDto invite(Authentication authentication,
                                 @Valid @RequestBody CreateInviteRequest request) {
        return coachLinkService.invite(currentUserId(authentication), request);
    }

    @GetMapping("/me/coach-invites")
    public List<CoachInviteDto> listIncoming(Authentication authentication) {
        return coachLinkService.listIncoming(currentUserId(authentication));
    }

    @PostMapping("/me/coach-invites/{id:\\d+}/accept")
    public CoachInviteDto accept(Authentication authentication,
                                 @PathVariable("id") Long id) {
        return coachLinkService.accept(currentUserId(authentication), id);
    }

    @PostMapping("/me/coach-invites/{id:\\d+}/decline")
    public CoachInviteDto decline(Authentication authentication,
                                  @PathVariable("id") Long id) {
        return coachLinkService.decline(currentUserId(authentication), id);
    }

    private Long currentUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal up) return up.getId();
        throw new IllegalStateException("Unexpected principal: " + principal.getClass());
    }
}
