package com.example.athletehub.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite primary key for {@link PostLike} — JPA's {@code @IdClass}
 * needs a serializable POJO mirroring the entity's id fields with
 * {@code equals}/{@code hashCode}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PostLikeKey implements Serializable {
    private Long postId;
    private Long userId;
}
