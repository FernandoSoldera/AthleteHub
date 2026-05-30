package com.example.athletehub.service;

import com.example.athletehub.dto.CreateManualPostRequest;
import com.example.athletehub.dto.PostDto;
import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.exception.ResourceNotFoundException;
import com.example.athletehub.model.CardioActivity;
import com.example.athletehub.model.Evaluation;
import com.example.athletehub.model.Post;
import com.example.athletehub.model.WorkoutSession;
import com.example.athletehub.repository.FollowRepository;
import com.example.athletehub.repository.PostRepository;
import com.example.athletehub.repository.UserCountersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * AH-061 — auto-create posts from training / cardio / evaluation events
 * + manual posts.
 *
 * <h2>Auto-publish hooks</h2>
 *
 * The three {@code publishFrom*} methods snapshot the source row's
 * rollup fields into a JSONB {@code payload} (Map<String, Object>), set
 * {@code source_ref_type} + {@code source_ref_id} to the soft link, and
 * stamp {@code type} per the data-model spec. Callers wrap these in
 * try/catch so a snapshot failure can't roll back the originating
 * transaction — a workout that finished should stay finished even if
 * the feed card couldn't be persisted (the user can manually re-post).
 *
 * <h2>Counter math</h2>
 *
 * Every publish bumps {@code user_counters.posts} by 1; every soft-
 * delete drops it by 1. The counter reflects the user's <em>visible</em>
 * post count, so soft-deleted rows are excluded (matches what the feed
 * read endpoints will surface).
 */
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserCountersRepository userCountersRepository;
    private final FollowRepository followRepository;

    // ── auto-publish from training (AH-033 finishSession) ─────────────────

    /**
     * Snapshot a finished workout into a feed card.
     */
    @Transactional
    public PostDto publishFromWorkout(WorkoutSession session) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", session.getTitle());
        payload.put("totalVolumeKg", session.getTotalVolumeKg());
        payload.put("totalSets", session.getTotalSets());
        payload.put("prCount", session.getPrCount());
        if (session.getDurationSeconds() != null) {
            payload.put("durationSeconds", session.getDurationSeconds());
        }

        Post post = persistAndCount(Post.builder()
                .authorId(session.getUserId())
                .type("workout")
                .title(session.getTitle())
                .sourceRefType("workout_session")
                .sourceRefId(session.getId())
                .payload(payload)
                .visibility("followers")
                .build());
        return PostDto.from(post);
    }

    // ── auto-publish from cardio (AH-034 CardioService.create) ────────────

    @Transactional
    public PostDto publishFromCardio(CardioActivity activity) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", activity.getType());
        payload.put("distanceM", activity.getDistanceM());
        payload.put("durationSeconds", activity.getDurationSeconds());
        if (activity.getAvgPaceSPerKm() != null) {
            payload.put("avgPaceSPerKm", activity.getAvgPaceSPerKm());
        }
        if (activity.getAvgHr() != null) payload.put("avgHr", activity.getAvgHr());
        if (activity.getKcal() != null) payload.put("kcal", activity.getKcal());

        // Cardio type → post type. The schema accepts walk too; we'll
        // tag walk-events as 'run' on the post side since the design's
        // type enum doesn't distinguish them (walk renders the same card).
        String postType = switch (activity.getType()) {
            case "cycle" -> "cycle";
            default -> "run";  // 'run' covers run + walk
        };

        Post post = persistAndCount(Post.builder()
                .authorId(activity.getUserId())
                .type(postType)
                .sourceRefType("cardio_activity")
                .sourceRefId(activity.getId())
                .payload(payload)
                .visibility("followers")
                .build());
        return PostDto.from(post);
    }

    // ── auto-publish from evaluation (AH-041 EvaluationService.create) ────

    @Transactional
    public PostDto publishFromEvaluation(Evaluation evaluation) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("weightKg", evaluation.getWeightKg());
        if (evaluation.getBodyFatPct() != null) {
            payload.put("bodyFatPct", evaluation.getBodyFatPct());
        }
        if (evaluation.getBfMethod() != null) {
            payload.put("bfMethod", evaluation.getBfMethod());
        }
        payload.put("evaluatedAt", evaluation.getEvaluatedAt());

        Post post = persistAndCount(Post.builder()
                .authorId(evaluation.getUserId())
                .type("evolution")
                .sourceRefType("evaluation")
                .sourceRefId(evaluation.getId())
                .payload(payload)
                .visibility("followers")
                .build());
        return PostDto.from(post);
    }

    // ── manual posts ──────────────────────────────────────────────────────

    @Transactional
    public PostDto publishManual(Long userId, CreateManualPostRequest request) {
        Post post = persistAndCount(Post.builder()
                .authorId(userId)
                .type("manual")
                .title(request.getTitle() == null || request.getTitle().isBlank()
                        ? null : request.getTitle().trim())
                .note(request.getNote() == null || request.getNote().isBlank()
                        ? null : request.getNote().trim())
                .visibility(request.getVisibility() == null
                        ? "followers" : request.getVisibility())
                .build());
        return PostDto.from(post);
    }

    // ── soft-delete ───────────────────────────────────────────────────────

    /**
     * Soft-delete by stamping {@code deleted_at = now()} on the row and
     * decrementing the user's posts counter. The post stays in the DB so
     * its likes / comments retain context (hard delete only on GDPR via
     * the user-CASCADE chain).
     */
    @Transactional
    public void softDelete(Long userId, Long postId) {
        Post post = postRepository.findById(postId)
                .filter(p -> p.getAuthorId().equals(userId))
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.POST_NOT_FOUND));
        post.setDeletedAt(OffsetDateTime.now());
        postRepository.save(post);
        userCountersRepository.adjustPosts(userId, -1);
    }

    // ── visibility chokepoint (AH-063) ────────────────────────────────────

    /**
     * Loads an active (not soft-deleted) post the viewer is allowed to
     * see, or throws {@code POST_NOT_FOUND}. Used by the
     * like / comment / list-comments paths so visibility logic doesn't
     * fork across endpoints.
     *
     * <p>Visibility rule:
     * <ul>
     *   <li>own posts: always</li>
     *   <li>{@code public}: anyone</li>
     *   <li>{@code followers}: only if the viewer follows the author</li>
     *   <li>{@code private}: author only (same as own)</li>
     * </ul>
     *
     * <p>"Doesn't exist" and "not allowed to see" return the same code so
     * we don't leak existence by timing or status.
     */
    @Transactional(readOnly = true)
    public Post loadVisible(Long viewerId, Long postId) {
        Post post = postRepository.findById(postId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.POST_NOT_FOUND));
        if (canView(viewerId, post)) return post;
        throw new ResourceNotFoundException(MessageCode.POST_NOT_FOUND);
    }

    private boolean canView(Long viewerId, Post post) {
        if (post.getAuthorId().equals(viewerId)) return true;
        return switch (post.getVisibility()) {
            case "public" -> true;
            case "followers" -> followRepository
                    .findByFollowerIdAndFolloweeId(viewerId, post.getAuthorId())
                    .isPresent();
            default -> false;  // 'private' — only the author (handled above)
        };
    }

    // ── helpers ───────────────────────────────────────────────────────────

    /** Save the row and bump the user-posts counter in one place. */
    private Post persistAndCount(Post post) {
        Post saved = postRepository.save(post);
        userCountersRepository.adjustPosts(saved.getAuthorId(), 1);
        return saved;
    }
}
