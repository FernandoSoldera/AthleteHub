package com.example.athletehub.dto;

/**
 * One item on a feed page — the post + the hydrated author + the
 * viewer-scoped {@code iLiked} flag. Hydration is batched once per
 * page so a 20-row page is two extra SELECTs (authors + likes),
 * not 40.
 */
public record FeedItemDto(
        PostDto post,
        PublicUserDto author,
        boolean iLiked
) {}
