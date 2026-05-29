package com.example.athletehub.dto;

import java.time.OffsetDateTime;

/**
 * One favorite as seen by the client — the food is materialized so the
 * Quick-Add list renders the macro labels without a follow-up call.
 */
public record FavoriteDto(
        Long id,
        OffsetDateTime createdAt,
        FoodDto food
) {}
