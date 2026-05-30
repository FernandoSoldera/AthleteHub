package com.example.athletehub.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * One row per published feed item. {@code type} discriminates how the
 * card renders ({@code workout | run | cycle | evolution | manual}).
 * {@code sourceRefType} + {@code sourceRefId} are a soft link back to
 * the row that triggered the auto-post — no FK because we don't want a
 * source-row delete to be blocked by a post; the link goes stale
 * gracefully.
 *
 * <p>{@code payload} is a JSONB snapshot of what the card rendered at
 * publish time (stats, sparkline values, before/after labels). Snapshot
 * semantics mean a coach renaming an exercise tomorrow doesn't rewrite
 * yesterday's feed cards. {@code @JdbcTypeCode(SqlTypes.JSON)} lets us
 * map to a {@code Map<String, Object>} directly — Hibernate handles
 * Jackson serialization on read/write.
 *
 * <p>{@code likeCount} + {@code commentCount} are denormalized counters
 * maintained by the service so a feed card render is O(1). Soft-delete
 * via {@code deletedAt} so threads + counter history stay consistent.
 */
@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(nullable = false)
    private String type;

    private String title;

    private String note;

    @Column(name = "source_ref_type")
    private String sourceRefType;

    @Column(name = "source_ref_id")
    private Long sourceRefId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "image_media_id")
    private Long imageMediaId;

    @Column(nullable = false)
    private String visibility;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (visibility == null) visibility = "followers";
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
