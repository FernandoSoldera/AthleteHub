package com.example.athletehub.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * One comment on a post. Soft-delete via {@code deletedAt} — the row
 * stays in the table for moderation audit and so the thread doesn't
 * orphan its replies (if reply-to-comment ever lands). Hard delete only
 * on the user-CASCADE chain (GDPR erase).
 */
@Entity
@Table(name = "post_comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(nullable = false)
    private String body;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
