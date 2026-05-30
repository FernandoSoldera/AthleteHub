package com.example.athletehub.service;

import com.example.athletehub.dto.CursorPage;
import com.example.athletehub.dto.FeedItemDto;
import com.example.athletehub.dto.PostDto;
import com.example.athletehub.dto.PublicUserDto;
import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.exception.ResourceNotFoundException;
import com.example.athletehub.model.Post;
import com.example.athletehub.model.User;
import com.example.athletehub.repository.FollowRepository;
import com.example.athletehub.repository.PostLikeRepository;
import com.example.athletehub.repository.PostRepository;
import com.example.athletehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * AH-062 — feed reads (home timeline + profile feed) with author +
 * iLiked hydration.
 *
 * <h2>Visibility</h2>
 * <ul>
 *   <li><b>public</b> — visible to anyone.</li>
 *   <li><b>followers</b> — author + author's followers.</li>
 *   <li><b>private</b> — author only.</li>
 * </ul>
 *
 * <h2>Home feed</h2>
 *
 * Fan-out-on-read: the viewer sees their own posts (any visibility,
 * including private) plus their followees' public / followers posts.
 * Public posts from non-followed users do <em>not</em> appear here —
 * that's a future "explore" feed, not the home timeline.
 *
 * <h2>Profile feed</h2>
 *
 * Filtered by author + viewer relationship. Self gets all; followers get
 * public + followers; strangers get public only. Pre-computes the
 * viewer-follows-author flag once per request.
 *
 * <h2>Hydration</h2>
 *
 * Per page: one batched user load for authors + one batched like-IN load
 * for the viewer-scoped {@code iLiked} flag. Total cost is O(1) extra
 * queries per page regardless of page size.
 */
@Service
@RequiredArgsConstructor
public class FeedService {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;

    /** Visibility values accepted on the wire — matches the schema CHECK. */
    private static final Set<String> KNOWN_TYPES =
            Set.of("workout", "run", "cycle", "evolution", "manual");

    // ── home feed ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CursorPage<FeedItemDto> getHomeFeed(Long viewerId, Long cursor, int limit, String typeCsv) {
        List<String> types = parseTypes(typeCsv);
        // limit + 1 trick to detect more pages.
        List<Post> rows = types == null
                ? postRepository.findHomeFeed(viewerId, cursor, PageRequest.of(0, limit + 1))
                : postRepository.findHomeFeedByTypes(viewerId, types, cursor, PageRequest.of(0, limit + 1));
        return hydratePage(viewerId, rows, limit);
    }

    // ── profile feed ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CursorPage<FeedItemDto> getProfileFeed(Long viewerId, String handle, Long cursor, int limit) {
        User author = userRepository.findByHandle(handle.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.RESOURCE_NOT_FOUND));

        Set<String> allowed = allowedVisibilitiesFor(viewerId, author.getId());
        List<Post> rows = postRepository.findProfileFeed(
                author.getId(), allowed, cursor, PageRequest.of(0, limit + 1));
        return hydratePage(viewerId, rows, limit);
    }

    // ── shared hydration + helpers ────────────────────────────────────────

    private CursorPage<FeedItemDto> hydratePage(Long viewerId, List<Post> rows, int limit) {
        if (rows.isEmpty()) return CursorPage.of(List.of(), null);

        boolean hasMore = rows.size() > limit;
        List<Post> visible = hasMore ? rows.subList(0, limit) : rows;

        // Author hydration in one batched read.
        Set<Long> authorIds = new HashSet<>();
        Set<Long> postIds = new HashSet<>();
        for (Post p : visible) {
            authorIds.add(p.getAuthorId());
            postIds.add(p.getId());
        }
        Map<Long, User> authorsById = new HashMap<>();
        userRepository.findAllById(authorIds).forEach(u -> authorsById.put(u.getId(), u));

        // iLiked hydration in one batched read.
        Set<Long> likedPostIds = new HashSet<>(
                postLikeRepository.findLikedPostIds(viewerId, postIds));

        List<FeedItemDto> items = new ArrayList<>(visible.size());
        for (Post p : visible) {
            User author = authorsById.get(p.getAuthorId());
            items.add(new FeedItemDto(
                    PostDto.from(p),
                    author == null ? null : PublicUserDto.from(author),
                    likedPostIds.contains(p.getId())));
        }
        String nextCursor = hasMore
                ? String.valueOf(visible.get(visible.size() - 1).getId())
                : null;
        return CursorPage.of(items, nextCursor);
    }

    /**
     * Visibility set the viewer is allowed to see on the author's profile:
     *   * self → all three
     *   * follower → public + followers
     *   * stranger → public
     */
    private Set<String> allowedVisibilitiesFor(Long viewerId, Long authorId) {
        if (viewerId.equals(authorId)) {
            return Set.of("public", "followers", "private");
        }
        boolean viewerFollowsAuthor = followRepository
                .findByFollowerIdAndFolloweeId(viewerId, authorId).isPresent();
        if (viewerFollowsAuthor) {
            return Set.of("public", "followers");
        }
        return Set.of("public");
    }

    /**
     * Parse {@code ?type=workout,run,...} into a filtered list (only the
     * known enum values survive — silently drops unknown ones rather than
     * failing the request). Returns null when the param is null/blank /
     * filters to empty, signalling the caller to take the unfiltered branch.
     */
    private static List<String> parseTypes(String csv) {
        if (csv == null || csv.isBlank()) return null;
        List<String> out = new ArrayList<>();
        for (String t : csv.split(",")) {
            String trimmed = t.trim();
            if (KNOWN_TYPES.contains(trimmed)) out.add(trimmed);
        }
        return out.isEmpty() ? null : out;
    }
}
