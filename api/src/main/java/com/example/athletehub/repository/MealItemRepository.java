package com.example.athletehub.repository;

import com.example.athletehub.model.MealItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MealItemRepository extends JpaRepository<MealItem, Long> {

    /**
     * Pulls all items across a batch of meals — used by the plan-hydration
     * pass so one round-trip materializes every item instead of N+1.
     * Sorted (meal_id, position) so the caller can group/iterate in one
     * walk.
     */
    List<MealItem> findByMealIdInOrderByMealIdAscPositionAsc(List<Long> mealIds);
}
