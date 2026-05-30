package com.example.athletehub.service;

import com.example.athletehub.dto.ConversationDto;
import com.example.athletehub.dto.CursorPage;
import com.example.athletehub.dto.MessageDto;
import com.example.athletehub.dto.PublicUserDto;
import com.example.athletehub.dto.SendMessageRequest;
import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.exception.ResourceNotFoundException;
import com.example.athletehub.model.CoachAthlete;
import com.example.athletehub.model.Conversation;
import com.example.athletehub.model.ConversationParticipant;
import com.example.athletehub.model.Message;
import com.example.athletehub.repository.CoachAthleteRepository;
import com.example.athletehub.repository.ConversationParticipantRepository;
import com.example.athletehub.repository.ConversationRepository;
import com.example.athletehub.repository.MessageRepository;
import com.example.athletehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AH-081 — conversations + messages. Four flows:
 *
 * <ul>
 *   <li><b>inbox</b> — list the caller's conversations newest-first with
 *       hydrated peer + unread count.</li>
 *   <li><b>thread</b> — paginate one conversation's messages newest-first
 *       (cursor by id, same pattern as AH-074 assignments).</li>
 *   <li><b>send</b> — append a message, bump
 *       {@code last_message_at}/{@code last_message_preview} on the
 *       conversation row.</li>
 *   <li><b>read</b> — advance the caller's
 *       {@code last_read_message_id} to the latest message id, capping
 *       unread to zero.</li>
 * </ul>
 *
 * <p><b>Visibility chokepoint:</b> every entry point routes through
 * {@link #loadVisible(Long, Long)} which returns a single 404
 * ({@code CONVERSATION_NOT_FOUND}) for both "doesn't exist" and "not a
 * participant" — exposing the difference would let a coach enumerate
 * conversation ids.
 *
 * <p>Conversation rows are created lazily by
 * {@link #findOrCreateForRelationship(CoachAthlete)} — first send (or first
 * inbox-by-relationship open) on a relationship row creates the row + both
 * participant rows. Same find-or-create pattern as
 * {@code CoachLinkService.invite}.
 */
@Service
@RequiredArgsConstructor
public class MessagingService {

    /** Preview cap — must match the CHECK constraint on conversations. */
    private static final int PREVIEW_MAX = 280;

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final CoachAthleteRepository coachAthleteRepository;
    private final UserRepository userRepository;

    // ── inbox ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CursorPage<ConversationDto> listInbox(Long viewerId,
                                                 OffsetDateTime cursor,
                                                 int limit) {
        var page = PageRequest.of(0, limit + 1);
        List<Long> ids = (cursor == null)
                ? participantRepository.findInboxConversationIds(viewerId, page)
                : participantRepository.findInboxConversationIdsBefore(viewerId, cursor, page);

        boolean hasMore = ids.size() > limit;
        if (hasMore) ids = ids.subList(0, limit);
        if (ids.isEmpty()) return new CursorPage<>(List.of(), null);

        // Preserve the inbox order (lastMessageAt DESC NULLS LAST) when
        // hydrating: findAllById doesn't guarantee order, so we re-sort.
        Map<Long, Conversation> byId = new HashMap<>();
        for (Conversation c : conversationRepository.findAllById(ids)) byId.put(c.getId(), c);

        List<ConversationDto> items = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Conversation c = byId.get(id);
            if (c == null) continue; // race: conversation deleted mid-page
            items.add(hydrate(c, viewerId));
        }

        String nextCursor = null;
        if (hasMore) {
            Conversation lastWithTimestamp = null;
            for (int i = items.size() - 1; i >= 0; i--) {
                if (items.get(i).lastMessageAt() != null) {
                    lastWithTimestamp = byId.get(items.get(i).id());
                    break;
                }
            }
            if (lastWithTimestamp != null && lastWithTimestamp.getLastMessageAt() != null) {
                nextCursor = lastWithTimestamp.getLastMessageAt().toString();
            }
        }
        return new CursorPage<>(items, nextCursor);
    }

    // ── thread ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CursorPage<MessageDto> listMessages(Long viewerId,
                                               Long conversationId,
                                               Long cursor,
                                               int limit) {
        loadVisible(viewerId, conversationId); // chokepoint
        var page = PageRequest.of(0, limit + 1);
        List<Message> rows = (cursor == null)
                ? messageRepository.findFirstPage(conversationId, page)
                : messageRepository.findBefore(conversationId, cursor, page);

        boolean hasMore = rows.size() > limit;
        if (hasMore) rows = rows.subList(0, limit);
        List<MessageDto> items = rows.stream().map(MessageDto::from).toList();
        String next = hasMore && !items.isEmpty()
                ? String.valueOf(items.get(items.size() - 1).id())
                : null;
        return new CursorPage<>(items, next);
    }

    // ── send ─────────────────────────────────────────────────────────────

    @Transactional
    public MessageDto sendMessage(Long senderId,
                                  Long conversationId,
                                  SendMessageRequest request) {
        Conversation convo = loadVisible(senderId, conversationId);
        Message saved = messageRepository.save(Message.builder()
                .conversationId(convo.getId())
                .senderId(senderId)
                .body(request.getBody())
                .build());

        // Bump denormalised inbox-list fields.
        convo.setLastMessageAt(saved.getCreatedAt());
        convo.setLastMessagePreview(truncatePreview(request.getBody()));
        conversationRepository.save(convo);

        // Auto-advance the sender's read pointer to their own message —
        // they obviously "read" what they just sent.
        participantRepository.findByConversationIdAndUserId(convo.getId(), senderId)
                .ifPresent(p -> {
                    p.setLastReadMessageId(saved.getId());
                    participantRepository.save(p);
                });

        return MessageDto.from(saved);
    }

    // ── read ─────────────────────────────────────────────────────────────

    @Transactional
    public void markRead(Long viewerId, Long conversationId) {
        loadVisible(viewerId, conversationId);
        ConversationParticipant me = participantRepository
                .findByConversationIdAndUserId(conversationId, viewerId)
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.CONVERSATION_NOT_FOUND));
        Long latestId = messageRepository
                .findFirstByConversationIdOrderByIdDesc(conversationId)
                .map(Message::getId)
                .orElse(me.getLastReadMessageId());
        if (latestId != null && (me.getLastReadMessageId() == null
                || latestId > me.getLastReadMessageId())) {
            me.setLastReadMessageId(latestId);
            participantRepository.save(me);
        }
    }

    // ── lazy creation for a coach-athlete relationship ───────────────────

    /**
     * Returns the conversation tied to {@code coachAthleteId}, creating it
     * (and both participant rows) on first call. Used by the
     * "open chat with my coach / this athlete" path on the client — the
     * lookup is keyed off the relationship, not the conversation id, so
     * the client doesn't need to track ids before sending the first
     * message.
     */
    @Transactional
    public ConversationDto openForRelationship(Long viewerId, Long coachAthleteId) {
        CoachAthlete relationship = coachAthleteRepository.findById(coachAthleteId)
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.CONVERSATION_NOT_FOUND));
        if (!relationship.getCoachId().equals(viewerId)
                && !relationship.getAthleteId().equals(viewerId)) {
            // Same 404 for "not a participant" as for "doesn't exist".
            throw new ResourceNotFoundException(MessageCode.CONVERSATION_NOT_FOUND);
        }
        Conversation convo = findOrCreateForRelationship(relationship);
        return hydrate(convo, viewerId);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /**
     * Visibility chokepoint — single method every entry point goes through.
     * Same 404 for both "doesn't exist" and "not a participant" so a
     * caller can't tell the difference (timing safety).
     */
    private Conversation loadVisible(Long viewerId, Long conversationId) {
        Conversation convo = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.CONVERSATION_NOT_FOUND));
        boolean isParticipant = participantRepository
                .findByConversationIdAndUserId(conversationId, viewerId).isPresent();
        if (!isParticipant) {
            throw new ResourceNotFoundException(MessageCode.CONVERSATION_NOT_FOUND);
        }
        return convo;
    }

    private Conversation findOrCreateForRelationship(CoachAthlete relationship) {
        Optional<Conversation> existing = conversationRepository
                .findByCoachAthleteId(relationship.getId());
        if (existing.isPresent()) return existing.get();

        Conversation convo = conversationRepository.save(Conversation.builder()
                .coachAthleteId(relationship.getId())
                .build());
        participantRepository.save(ConversationParticipant.builder()
                .conversationId(convo.getId())
                .userId(relationship.getCoachId())
                .build());
        participantRepository.save(ConversationParticipant.builder()
                .conversationId(convo.getId())
                .userId(relationship.getAthleteId())
                .build());
        return convo;
    }

    private ConversationDto hydrate(Conversation convo, Long viewerId) {
        List<ConversationParticipant> participants =
                participantRepository.findByConversationId(convo.getId());

        Long peerUserId = null;
        Long viewerLastReadId = null;
        for (ConversationParticipant p : participants) {
            if (p.getUserId().equals(viewerId)) {
                viewerLastReadId = p.getLastReadMessageId();
            } else if (peerUserId == null) {
                peerUserId = p.getUserId();
            }
        }

        PublicUserDto peer = null;
        if (peerUserId != null) {
            peer = userRepository.findById(peerUserId)
                    .map(PublicUserDto::from)
                    .orElse(null);
        }
        long unread = messageRepository.countUnread(convo.getId(), viewerId, viewerLastReadId);
        return new ConversationDto(
                convo.getId(),
                convo.getCoachAthleteId(),
                convo.getLastMessageAt(),
                convo.getLastMessagePreview(),
                unread,
                peer);
    }

    private String truncatePreview(String body) {
        String trimmed = body.replace('\n', ' ').trim();
        if (trimmed.length() <= PREVIEW_MAX) return trimmed;
        return trimmed.substring(0, PREVIEW_MAX);
    }
}
