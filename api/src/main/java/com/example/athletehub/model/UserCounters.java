package com.example.athletehub.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Denormalized per-user totals. Maintained inside the same transaction as the
 * underlying event so reads can be done in a single round trip without
 * counting joins. A row is created for every user (in the user-creation path
 * and via a backfill in the AH-020 migration).
 */
@Entity
@Table(name = "user_counters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCounters {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Builder.Default
    @Column(nullable = false)
    private Integer followers = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer following = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer sessions = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer posts = 0;
}
