package com.example.athletehub.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Pins a {@link WorkoutTemplate} to a weekday (ISO: Mon = 1 … Sun = 7).
 * Used by {@code GET /api/training/today} to derive the planned session
 * for the caller without storing a denormalized per-day pointer.
 */
@Entity
@Table(name = "template_schedules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
