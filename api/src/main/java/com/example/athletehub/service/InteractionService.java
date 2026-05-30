package com.example.athletehub.service;

import com.example.athletehub.dto.CommentDto;
import com.example.athletehub.dto.CreateCommentRequest;
import com.example.athletehub.dto.CursorPage;
import com.example.athletehub.dto.PublicUserDto;
import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.exception.ResourceNotFoundException;
import com.example.athletehub.model.Post;
import com.example.athletehub.model.PostComment;
import com.example.athletehub.model.PostLike;
import com.example.athletehub.model.PostLikeKey;
import com.example.athletehub.model.User;
import com.example.athletehub.repository.PostCommentRepository;
import com.example.athletehub.repository.PostLikeRepository;
import com.example.athletehub.repository.PostRepository;
import com.example.athletehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AH-063 — likes + comments. Visibility is delegated to {@link
 * PostService#loadVisible} so the gating logic stays in one place.
 *
 * <h2>Likes</h2>
 *
 * Both {@code like} and {@code unlike} are <b>idempotent</b>: a second
 * tap is a no-op at the data layer. Counter increments only fire on the
 * actually-changed transitions.
 *
 * <h2>Comments</h2>
 *
 * Add returns the hydrated DTO so the client can render the new card
 * without a follow-up call. Delete is soft (stamps {@code deletedAt})
 * and decrements the post's comment counter; the row stays for
 * moderation audit. The thread endpoint excludes soft-deleted rows so
 * the wire shape only carries active comments.
 */
@Service
@RequiredArgsConstructor
public class InteractionService {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostCommentRepository postCommentRepository;
    private final UserRepository userRepository;
    private final PostService postService;

    // ── likes ─────────────────────────────────────────────────────────────

    /**
     * Like {@code postId} on behalf of {@code viewerId}. Idempotent —
     * find-or-insert. The like row + counter increment only fire when no
     * row existed.
     */
    @Transactional
    public void like(Long viewerId, Long postId) {
        Post post = postService.loadVisible(viewerId, postId);

        PostLikeKey key = new PostLikeKey(postId, viewerId);
        if (postLikeRepository.existsById(key)) return;

        postLikeRepository.save(PostLike.builder()
                .postId(post.getId())
                .userId(viewerId)
                .build());
        postRepository.adjustLikeCount(post.getId(), 1);
    }

    /**
     * Unlike. Idempotent — second call is a no-op. Counter decrement only
     * fires when a row actually existed (so the schema's
     * {@code like_count >= 0} CHECK never trips).
     *
     * <p>Doesn't re-check visibility: a user who liked a public post then
     * had the author flip it to private should still be able to unlike.
     */
    @Transactional
    public void unlike(Long viewerId, Long postId) {
        PostLikeKey key = new PostLikeKey(postId, viewerId);
        if (!postLikeRepository.existsById(key)) return;
        postLikeRepository.deleteById(key);
        postRepository.adjustLikeCount(postId, -1);
    }

    // ── comments ──────────────────────────────────────────────────────────

    @Transactional
    public CommentDto addComment(Long viewerId, Long postId, CreateCommentRequest request) {
        Post post = postService.loadVisible(viewerId, postId);

        PostComment saved = postCommentRepository.save(PostComment.builder()
                .postId(post.getId())
                .authorId(viewerId)
                .body(request.getBody().trim())
                .build());
        postRepository.adjustCommentCount(post.getId(), 1);

        // Hydrate author for the response so the client doesn't need
        // a follow-up call to render the new card.
        User author = userRepository.findById(viewerId)
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.RESOURCE_NOT_FOUND));
        return new CommentDto(
                saved.getId(),
                saved.getPostId(),
                saved.getBody(),
                saved.getCreatedAt(),
                PublicUserDto.from(author));
    }

    @Transactional
    public void deleteComment(Long viewerId, Long commentId) {
        PostComment comment = postCommentRepository.findById(commentId)
                .filter(c -> c.getAuthorId().equals(viewerId))
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.COMMENT_NOT_FOUND));
        comment.setDeletedAt(OffsetDateTime.now());
        postCommentRepository.save(comment);
        postRepository.adjustCommentCount(comment.getPostId(), -1);
    }

    @Transactional(readOnly = true)
    public CursorPage<CommentDto> listComments(Long viewerId, Long postId, Long cursor, int limit) {
        // Visibility gate even for reading the thread.
        postService.loadVisible(viewerId, postId);

        List<PostComment> rows = postCommentRepository.findThread(
                postId, cursor, PageRequest.of(0, limit + 1));
        if (rows.isEmpty()) return CursorPage.of(List.of(), null);

        boolean hasMore = rows.size() > limit;
        List<PostComment> visible = hasMore ? rows.subList(0, limit) : rows;

        // Batch-hydrate authors.
        Set<Long> authorIds = new HashSet<>();
        for (PostComment c : visible) authorIds.add(c.getAuthorId());
        Map<Long, User> authorsById = new HashMap<>();
        userRepository.findAllById(authorIds).forEach(u -> authorsById.put(u.getId(), u));

        List<CommentDto> items = new ArrayList<>(visible.size());
        for (PostComment c : visible) {
            User author = authorsById.get(c.getAuthorId());
            items.add(new CommentDto(
                    c.getId(),
                    c.getPostId(),
                    c.getBody(),
                    c.getCreatedAt(),
                    author == null ? null : PublicUserDto.from(author)));
        }
        String nextCursor = hasMore
                ? String.valueOf(visible.get(visible.size() - 1).getId())
                : null;
        return CursorPage.of(items, nextCursor);
    }
}
