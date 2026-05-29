package com.example.athletehub.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body for {@code POST /api/diet/favorites}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddFavoriteRequest {

    @NotNull
    private Long foodId;
}
