package com.example.athletehub.dto;

import java.time.OffsetDateTime;

/**
 * One comment + its hydrated author. The thread endpoint omits
 * soft-deleted rows, so {@code deletedAt} isn't exposed on the wire —
 * the wire shape only carries active comments.
 */
public record CommentDto(
        Long id,
        Long postId,
        String body,
        OffsetDateTime createdAt,
        PublicUserDto author
) {}
