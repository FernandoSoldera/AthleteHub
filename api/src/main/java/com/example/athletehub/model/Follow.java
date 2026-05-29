package com.example.athletehub.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * A directed follow edge. We store raw {@code follower_id} / {@code followee_id}
 * rather than {@link User} associations because we almost never need to
 * navigate from a follow row back to the full user — and avoiding lazy
 * proxies makes batch hydration straightforward.
 */
@Entity
@Table(name = "follows")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Follow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "follower_id", nullable = false)
    private Long followerId;

    @Column(name = "followee_id", nullable = false)
    private Long followeeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
