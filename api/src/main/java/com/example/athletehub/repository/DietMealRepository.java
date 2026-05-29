package com.example.athletehub.repository;

import com.example.athletehub.model.DietMeal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DietMealRepository extends JpaRepository<DietMeal, Long> {

    /** Meals of {@code planId}, ordered by position. */
    List<DietMeal> findByPlanIdOrderByPositionAsc(Long planId);
}
