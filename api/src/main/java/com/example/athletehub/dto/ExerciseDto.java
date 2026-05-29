package com.example.athletehub.dto;

import com.example.athletehub.model.Exercise;

/**
 * Public view of an exercise. {@code custom} flips true for the caller's
 * own additions so the client can label them ("Custom" badge) without
 * re-deriving from {@code createdBy}. We don't expose the owner id because
 * customs are private to their creator — if you can see one, you own it.
 */
public record ExerciseDto(
        Long id,
        String name,
        String category,
        String primaryMuscle,
        String equipment,
        boolean custom
) {
    public static ExerciseDto from(Exercise e) {
        return new ExerciseDto(
                e.getId(),
                e.getName(),
                e.getCategory(),
                e.getPrimaryMuscle(),
                e.getEquipment(),
                !e.isGlobal()
        );
    }
}
