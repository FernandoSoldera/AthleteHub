package com.example.athletehub.dto;

import java.util.List;

/**
 * Cursor-paginated response envelope. {@code nextCursor} is null when there
 * are no more pages; otherwise the client passes it back as the {@code cursor}
 * query parameter to fetch the next page.
 */
public record CursorPage<T>(List<T> items, String nextCursor) {

    public static <T> CursorPage<T> of(List<T> items, String nextCursor) {
        return new CursorPage<>(items, nextCursor);
    }
}
