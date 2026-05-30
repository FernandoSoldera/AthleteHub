package com.example.athletehub.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * One "like" on a post — composite PK on {@code (post_id, user_id)}.
 * Used by AH-062 just for the batched {@code iLiked} hydration on the
 * feed read endpoints; the write paths (like / unlike) land in AH-063.
 */
@Entity
@Table(name = "post_likes")
@IdClass(PostLikeKey.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostLike {

    @Id
    @Column(name = "post_id")
    private Long postId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
