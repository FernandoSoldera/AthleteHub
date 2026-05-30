package com.example.athletehub.service;

import com.example.athletehub.dto.CoachInviteDto;
import com.example.athletehub.dto.CreateInviteRequest;
import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.exception.BadRequestException;
import com.example.athletehub.exception.ConflictException;
import com.example.athletehub.exception.ResourceNotFoundException;
import com.example.athletehub.model.CoachAthlete;
import com.example.athletehub.model.User;
import com.example.athletehub.repository.CoachAthleteRepository;
import com.example.athletehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AH-071 — coach↔athlete invite + consent linking. Four flows:
 *
 * <ul>
 *   <li><b>invite</b> — coach invites an athlete by handle. Creates a
 *       new {@code pending} row, or revives an {@code ended} one. An
 *       already-{@code pending} or {@code active} row → 409
 *       {@code COACH_LINK_EXISTS}.</li>
 *   <li><b>list incoming</b> — athlete's {@code pending} inbox.</li>
 *   <li><b>accept</b> — pending → active, stamps {@code since = today}.</li>
 *   <li><b>decline</b> — pending → ended.</li>
 * </ul>
 *
 * <p>Self-invite is blocked at the schema level (the
 * {@code coach_id <> athlete_id} CHECK) but we short-circuit here with a
 * friendlier 400 before the DB gets involved.
 */
@Service
@RequiredArgsConstructor
public class CoachLinkService {

    private final CoachAthleteRepository coachAthleteRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    // ── invite ────────────────────────────────────────────────────────────

    @Transactional
    public CoachInviteDto invite(Long coachId, CreateInviteRequest request) {
        String handle = request.getHandle().trim().toLowerCase(Locale.ROOT);
        User athlete = userRepository.findByHandle(handle)
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.RESOURCE_NOT_FOUND));
        if (athlete.getId().equals(coachId)) {
            throw new BadRequestException("Cannot coach yourself");
        }

        // Find-or-revive: an `ended` row flips back to pending; pending/active blocks.
        CoachAthlete row = coachAthleteRepository
                .findByCoachIdAndAthleteId(coachId, athlete.getId())
                .map(existing -> {
                    switch (existing.getStatus()) {
                        case "pending", "active" ->
                                throw new ConflictException(MessageCode.COACH_LINK_EXISTS);
                        case "ended" -> {
                            existing.setStatus("pending");
                            existing.setSince(null);
                            existing.setFlag(null);
                            existing.setAdherencePct(null);
                        }
                        default -> throw new IllegalStateException(
                                "Unexpected coach_athlete status: " + existing.getStatus());
                    }
                    return existing;
                })
                .orElseGet(() -> CoachAthlete.builder()
                        .coachId(coachId)
                        .athleteId(athlete.getId())
                        .status("pending")
                        .build());

        CoachAthlete saved = coachAthleteRepository.save(row);
        User coach = userRepository.findById(coachId)
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.RESOURCE_NOT_FOUND));
        return CoachInviteDto.from(saved, coach, athlete);
    }

    // ── athlete inbox ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CoachInviteDto> listIncoming(Long athleteId) {
        List<CoachAthlete> rows =
                coachAthleteRepository.findByAthleteIdAndStatusOrderByIdDesc(athleteId, "pending");
        if (rows.isEmpty()) return List.of();

        // Batch-hydrate the coach users. The athlete side is the same
        // person across every row, so we only need it once.
        Map<Long, User> coaches = new HashMap<>();
        List<Long> coachIds = new ArrayList<>();
        for (CoachAthlete r : rows) coachIds.add(r.getCoachId());
        userRepository.findAllById(coachIds).forEach(u -> coaches.put(u.getId(), u));

        User athlete = userRepository.findById(athleteId)
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.RESOURCE_NOT_FOUND));

        List<CoachInviteDto> out = new ArrayList<>(rows.size());
        for (CoachAthlete r : rows) {
            out.add(CoachInviteDto.from(r, coaches.get(r.getCoachId()), athlete));
        }
        return out;
    }

    // ── accept / decline ──────────────────────────────────────────────────

    @Transactional
    public CoachInviteDto accept(Long athleteId, Long inviteId) {
        CoachAthlete row = loadInviteFor(athleteId, inviteId);
        row.setStatus("active");
        row.setSince(LocalDate.now(clock));
        return hydrate(coachAthleteRepository.save(row));
    }

    @Transactional
    public CoachInviteDto decline(Long athleteId, Long inviteId) {
        CoachAthlete row = loadInviteFor(athleteId, inviteId);
        row.setStatus("ended");
        return hydrate(coachAthleteRepository.save(row));
    }

    // ── helpers ───────────────────────────────────────────────────────────

    /**
     * Loads an invite that's addressed to the caller AND still pending.
     * "Not yours" / "doesn't exist" return the same code so the API
     * doesn't leak existence via timing. "Not pending" returns a distinct
     * code so the client can render an honest "already accepted" message.
     */
    private CoachAthlete loadInviteFor(Long athleteId, Long inviteId) {
        CoachAthlete row = coachAthleteRepository.findById(inviteId)
                .filter(r -> r.getAthleteId().equals(athleteId))
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.INVITE_NOT_FOUND));
        if (!"pending".equals(row.getStatus())) {
            throw new ConflictException(MessageCode.INVITE_NOT_PENDING);
        }
        return row;
    }

    private CoachInviteDto hydrate(CoachAthlete row) {
        User coach = userRepository.findById(row.getCoachId()).orElse(null);
        User athlete = userRepository.findById(row.getAthleteId()).orElse(null);
        return CoachInviteDto.from(row, coach, athlete);
    }
}
