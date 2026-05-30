package com.example.athletehub.dto;

import com.example.athletehub.model.Post;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Public view of a {@link Post}. The {@code payload} JSON snapshot is
 * surfaced verbatim — the client renders the card from this map without
 * dereferencing the soft link to the source row.
 */
public record PostDto(
        Long id,
        Long authorId,
        String type,
        String title,
        String note,
        String sourceRefType,
        Long sourceRefId,
        Map<String, Object> payload,
        String visibility,
        int likeCount,
        int commentCount,
        OffsetDateTime createdAt
) {
    public static PostDto from(Post p) {
        return new PostDto(
                p.getId(),
                p.getAuthorId(),
                p.getType(),
                p.getTitle(),
                p.getNote(),
                p.getSourceRefType(),
                p.getSourceRefId(),
                p.getPayload(),
                p.getVisibility(),
                p.getLikeCount(),
                p.getCommentCount(),
                p.getCreatedAt()
        );
    }
}
