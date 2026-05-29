package com.example.athletehub.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A logged cardio session — run / walk / cycle. Most numeric fields are
 * optional because they come from different sources: a watch fills HR,
 * a power meter fills power, a phone with GPS fills pace + elevation,
 * a manual log might fill only distance + duration.
 *
 * <p>The CHECK constraints on type ∈ {run, walk, cycle} and source ∈
 * {self, assigned, import} live in the schema (V20260528150000); this
 * entity just mirrors the columns.
 */
@Entity
@Table(name = "cardio_activities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardioActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String type;

    @Column(name = "distance_m", nullable = false)
    private BigDecimal distanceM;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "avg_pace_s_per_km")
    private BigDecimal avgPaceSPerKm;

    @Column(name = "avg_power_w")
    private BigDecimal avgPowerW;

    @Column(name = "avg_hr")
    private Integer avgHr;

    @Column(name = "max_hr")
    private Integer maxHr;

    @Column(name = "elevation_gain_m")
    private BigDecimal elevationGainM;

    private Integer kcal;

    private String notes;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(nullable = false)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (startedAt == null) startedAt = now;
        if (source == null) source = "self";
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
