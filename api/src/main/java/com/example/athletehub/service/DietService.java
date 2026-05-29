package com.example.athletehub.service;

import com.example.athletehub.dto.AddFavoriteRequest;
import com.example.athletehub.dto.CreateDiaryEntryRequest;
import com.example.athletehub.dto.CursorPage;
import com.example.athletehub.dto.DayResponse;
import com.example.athletehub.dto.DiaryEntryDto;
import com.example.athletehub.dto.DietMealDto;
import com.example.athletehub.dto.DietPlanDto;
import com.example.athletehub.dto.FavoriteDto;
import com.example.athletehub.dto.FoodDto;
import com.example.athletehub.dto.Macros;
import com.example.athletehub.dto.MealItemDto;
import com.example.athletehub.dto.SetActivePlanRequest;
import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.exception.ResourceNotFoundException;
import com.example.athletehub.model.DiaryEntry;
import com.example.athletehub.model.DietMeal;
import com.example.athletehub.model.DietPlan;
import com.example.athletehub.model.Favorite;
import com.example.athletehub.model.Food;
import com.example.athletehub.model.MealItem;
import com.example.athletehub.model.User;
import com.example.athletehub.repository.DiaryEntryRepository;
import com.example.athletehub.repository.DietMealRepository;
import com.example.athletehub.repository.DietPlanRepository;
import com.example.athletehub.repository.FavoriteRepository;
import com.example.athletehub.repository.FoodRepository;
import com.example.athletehub.repository.MealItemRepository;
import com.example.athletehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * AH-052 — active diet plan + day endpoint. Three flows:
 *
 * <ul>
 *   <li>{@code getActivePlan(userId)} — returns the hydrated plan
 *       (meals → items → food) or null when no plan is active.</li>
 *   <li>{@code setActivePlan(userId, request)} — validates plan
 *       ownership; null planId clears the active pointer.</li>
 *   <li>{@code getDay(userId, date)} — aggregates the day's diary
 *       entries into totals, derives target from the active plan,
 *       and computes remaining = target − totals.</li>
 * </ul>
 *
 * <h2>Macro scaling</h2>
 *
 * For any (amount, unit, food) triple:
 * <ul>
 *   <li><b>g</b> / <b>ml</b> — {@code macro = amount × food.macro /
 *       food.serving_size_g}. Treats ml as g for now (most macro-relevant
 *       liquids — milk, juice, broth — are ~1 g/ml).</li>
 *   <li><b>portion</b> — {@code macro = amount × food.macro} (one
 *       portion = one × serving_size_g).</li>
 * </ul>
 *
 * Scale-2 HALF_UP on every output so JSON ↔ DB ↔ in-memory stays
 * round-trip stable with the {@code NUMERIC(7,2)} columns.
 */
@Service
@RequiredArgsConstructor
public class DietService {

    private final DietPlanRepository planRepository;
    private final DietMealRepository mealRepository;
    private final MealItemRepository itemRepository;
    private final DiaryEntryRepository diaryRepository;
    private final FavoriteRepository favoriteRepository;
    private final FoodRepository foodRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    // ── active plan ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DietPlanDto getActivePlan(Long userId) {
        Long planId = activePlanId(userId);
        if (planId == null) return null;
        return planRepository.findById(planId)
                .map(this::hydratePlan)
                .orElse(null);
    }

    @Transactional
    public DietPlanDto setActivePlan(Long userId, SetActivePlanRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.RESOURCE_NOT_FOUND));

        Long planId = request == null ? null : request.getPlanId();
        if (planId == null) {
            user.setActiveDietPlanId(null);
            userRepository.save(user);
            return null;
        }

        DietPlan plan = planRepository.findById(planId)
                .filter(p -> p.getOwnerId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.DIET_PLAN_NOT_FOUND));

        user.setActiveDietPlanId(plan.getId());
        userRepository.save(user);
        return hydratePlan(plan);
    }

    // ── day endpoint ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DayResponse getDay(Long userId, LocalDate date) {
        ZoneId zone = clock.getZone();
        LocalDate day = date != null ? date : LocalDate.now(clock);
        OffsetDateTime start = day.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = day.plusDays(1).atStartOfDay(zone).toOffsetDateTime();

        List<DiaryEntry> entries = diaryRepository.findByUserInRange(userId, start, end);

        // Batch-load every food the entries reference so we don't N+1.
        Set<Long> foodIds = new HashSet<>();
        for (DiaryEntry e : entries) foodIds.add(e.getFoodId());
        Map<Long, Food> foodsById = foodsByIdMap(foodIds);

        List<DiaryEntryDto> entryDtos = new ArrayList<>(entries.size());
        Macros totals = Macros.zero();
        for (DiaryEntry e : entries) {
            Food food = foodsById.get(e.getFoodId());
            Macros scaled = (food == null) ? Macros.zero() : scaleMacros(e.getAmount(), e.getUnit(), food);
            totals = add(totals, scaled);
            entryDtos.add(new DiaryEntryDto(
                    e.getId(),
                    e.getFoodId(),
                    food != null ? food.getName() : "(deleted)",
                    e.getEatenAt(),
                    e.getAmount(),
                    e.getUnit(),
                    e.getMealLabel(),
                    e.getSource(),
                    scaled));
        }

        // Target comes from the active plan (when set).
        Macros target = null;
        Macros remaining = null;
        Long planId = activePlanId(userId);
        if (planId != null) {
            target = planRepository.findById(planId).map(this::computePlanDailyTarget).orElse(null);
            if (target != null) {
                remaining = subtract(target, totals);
            }
        }
        return new DayResponse(day, entryDtos, round(totals), round(target), round(remaining));
    }

    // ── AH-053 — diary entries (POST / DELETE) ────────────────────────────

    @Transactional
    public DiaryEntryDto addDiaryEntry(Long userId, CreateDiaryEntryRequest request) {
        Food food = foodRepository.findByIdAndVisibleTo(request.getFoodId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.FOOD_NOT_FOUND));

        DiaryEntry saved = diaryRepository.save(DiaryEntry.builder()
                .userId(userId)
                .foodId(food.getId())
                .amount(request.getAmount())
                .unit(request.getUnit())
                .mealLabel(trimToNull(request.getMealLabel()))
                .eatenAt(request.getEatenAt())  // null → @PrePersist defaults to now()
                .source(request.getSource() == null ? "self" : request.getSource())
                .build());

        Macros scaled = round(scaleMacros(saved.getAmount(), saved.getUnit(), food));
        return new DiaryEntryDto(
                saved.getId(),
                saved.getFoodId(),
                food.getName(),
                saved.getEatenAt(),
                saved.getAmount(),
                saved.getUnit(),
                saved.getMealLabel(),
                saved.getSource(),
                scaled);
    }

    @Transactional
    public void deleteDiaryEntry(Long userId, Long entryId) {
        DiaryEntry entry = diaryRepository.findById(entryId)
                .filter(e -> e.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.DIARY_ENTRY_NOT_FOUND));
        diaryRepository.delete(entry);
    }

    // ── AH-053 — favorites (GET / POST / DELETE) ──────────────────────────

    @Transactional(readOnly = true)
    public CursorPage<FavoriteDto> listFavorites(Long userId, Long cursor, int limit) {
        List<Favorite> rows = favoriteRepository.findRecent(
                userId, cursor, PageRequest.of(0, limit + 1));
        boolean hasMore = rows.size() > limit;
        List<Favorite> visible = hasMore ? rows.subList(0, limit) : rows;

        // Batch-hydrate every food.
        Set<Long> foodIds = new HashSet<>();
        for (Favorite f : visible) foodIds.add(f.getFoodId());
        Map<Long, Food> foodsById = foodsByIdMap(foodIds);

        List<FavoriteDto> items = new ArrayList<>(visible.size());
        for (Favorite f : visible) {
            Food food = foodsById.get(f.getFoodId());
            // RESTRICT on favorites.food_id is CASCADE, so if a food vanished
            // the favorite is gone too — but if hydration misses for any
            // reason, we still emit the favorite with food = null rather
            // than skip it.
            items.add(new FavoriteDto(
                    f.getId(),
                    f.getCreatedAt(),
                    food == null ? null : FoodDto.from(food)));
        }
        String nextCursor = hasMore
                ? String.valueOf(visible.get(visible.size() - 1).getId())
                : null;
        return CursorPage.of(items, nextCursor);
    }

    /**
     * Find-or-insert. If the user already favourites this food, return the
     * existing row — the API is idempotent. Validates food visibility so
     * you can't favourite another user's custom (returns 404 FOOD_NOT_FOUND).
     */
    @Transactional
    public FavoriteDto addFavorite(Long userId, AddFavoriteRequest request) {
        Food food = foodRepository.findByIdAndVisibleTo(request.getFoodId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.FOOD_NOT_FOUND));

        Favorite favorite = favoriteRepository
                .findByUserIdAndFoodId(userId, food.getId())
                .orElseGet(() -> favoriteRepository.save(Favorite.builder()
                        .userId(userId)
                        .foodId(food.getId())
                        .build()));

        return new FavoriteDto(favorite.getId(), favorite.getCreatedAt(), FoodDto.from(food));
    }

    @Transactional
    public void removeFavorite(Long userId, Long foodId) {
        // Idempotent: deleting a missing favorite is a no-op (no 404).
        favoriteRepository.deleteByUserIdAndFoodId(userId, foodId);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private Long activePlanId(Long userId) {
        return userRepository.findById(userId)
                .map(User::getActiveDietPlanId)
                .orElse(null);
    }

    /**
     * Loads meals + items + foods for the plan in three batched queries,
     * scales each item's macros, and sums per-meal + per-plan totals.
     */
    private DietPlanDto hydratePlan(DietPlan plan) {
        List<DietMeal> meals = mealRepository.findByPlanIdOrderByPositionAsc(plan.getId());
        if (meals.isEmpty()) {
            return new DietPlanDto(
                    plan.getId(), plan.getName(), plan.getDescription(),
                    plan.isLibrary(), List.of(), Macros.zero());
        }
        List<Long> mealIds = meals.stream().map(DietMeal::getId).toList();
        List<MealItem> allItems = itemRepository.findByMealIdInOrderByMealIdAscPositionAsc(mealIds);

        Set<Long> foodIds = new HashSet<>();
        for (MealItem mi : allItems) foodIds.add(mi.getFoodId());
        Map<Long, Food> foodsById = foodsByIdMap(foodIds);

        Map<Long, List<MealItem>> itemsByMeal = new HashMap<>();
        for (MealItem mi : allItems) {
            itemsByMeal.computeIfAbsent(mi.getMealId(), k -> new ArrayList<>()).add(mi);
        }

        List<DietMealDto> mealDtos = new ArrayList<>();
        Macros dailyTotal = Macros.zero();
        for (DietMeal meal : meals) {
            List<MealItem> mealItems = itemsByMeal.getOrDefault(meal.getId(), List.of());
            List<MealItemDto> itemDtos = new ArrayList<>();
            for (MealItem mi : mealItems) {
                Food food = foodsById.get(mi.getFoodId());
                Macros scaled = (food == null) ? Macros.zero()
                        : scaleMacros(mi.getAmount(), mi.getUnit(), food);
                dailyTotal = add(dailyTotal, scaled);
                itemDtos.add(new MealItemDto(
                        mi.getId(), mi.getPosition(), mi.getAmount(), mi.getUnit(),
                        food != null ? FoodDto.from(food) : null,
                        round(scaled)));
            }
            mealDtos.add(new DietMealDto(
                    meal.getId(), meal.getPosition(), meal.getName(), meal.getTimeHint(),
                    itemDtos));
        }

        return new DietPlanDto(
                plan.getId(), plan.getName(), plan.getDescription(),
                plan.isLibrary(), mealDtos, round(dailyTotal));
    }

    /**
     * Total macros for one plan summed over every meal × item. Same load
     * pattern as {@link #hydratePlan} but returns just the totals (the day
     * endpoint doesn't need the meal tree).
     */
    private Macros computePlanDailyTarget(DietPlan plan) {
        List<DietMeal> meals = mealRepository.findByPlanIdOrderByPositionAsc(plan.getId());
        if (meals.isEmpty()) return Macros.zero();
        List<Long> mealIds = meals.stream().map(DietMeal::getId).toList();
        List<MealItem> allItems = itemRepository.findByMealIdInOrderByMealIdAscPositionAsc(mealIds);

        Set<Long> foodIds = new HashSet<>();
        for (MealItem mi : allItems) foodIds.add(mi.getFoodId());
        Map<Long, Food> foodsById = foodsByIdMap(foodIds);

        Macros total = Macros.zero();
        for (MealItem mi : allItems) {
            Food food = foodsById.get(mi.getFoodId());
            if (food == null) continue;
            total = add(total, scaleMacros(mi.getAmount(), mi.getUnit(), food));
        }
        return total;
    }

    private Map<Long, Food> foodsByIdMap(Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, Food> out = new HashMap<>();
        for (Food f : foodRepository.findAllById(ids)) out.put(f.getId(), f);
        return out;
    }

    /**
     * Scales the food's per-serving macros to the actual {@code amount} +
     * {@code unit}. See the class doc for the rule.
     */
    private static Macros scaleMacros(BigDecimal amount, String unit, Food food) {
        BigDecimal factor;
        switch (unit) {
            case "g", "ml" -> factor = amount.divide(food.getServingSizeG(), 6, RoundingMode.HALF_UP);
            case "portion" -> factor = amount;
            default -> throw new IllegalStateException("Unsupported unit: " + unit);
        }
        return new Macros(
                mul(food.getKcal(), factor),
                mul(food.getProteinG(), factor),
                mul(food.getCarbG(), factor),
                mul(food.getFatG(), factor),
                mul(food.getFiberG(), factor),
                mul(food.getSodiumMg(), factor));
    }

    private static BigDecimal mul(BigDecimal value, BigDecimal factor) {
        return value == null ? null : value.multiply(factor);
    }

    private static Macros add(Macros a, Macros b) {
        return new Macros(
                addOrZero(a.kcal(), b.kcal()),
                addOrZero(a.proteinG(), b.proteinG()),
                addOrZero(a.carbG(), b.carbG()),
                addOrZero(a.fatG(), b.fatG()),
                addOrZero(a.fiberG(), b.fiberG()),
                addOrZero(a.sodiumMg(), b.sodiumMg()));
    }

    private static Macros subtract(Macros a, Macros b) {
        return new Macros(
                subOrA(a.kcal(), b.kcal()),
                subOrA(a.proteinG(), b.proteinG()),
                subOrA(a.carbG(), b.carbG()),
                subOrA(a.fatG(), b.fatG()),
                subOrA(a.fiberG(), b.fiberG()),
                subOrA(a.sodiumMg(), b.sodiumMg()));
    }

    private static BigDecimal addOrZero(BigDecimal x, BigDecimal y) {
        BigDecimal xx = x == null ? BigDecimal.ZERO : x;
        BigDecimal yy = y == null ? BigDecimal.ZERO : y;
        return xx.add(yy);
    }

    /** Subtract; treat nulls on the right as zero so "no fiber consumed" doesn't blow up. */
    private static BigDecimal subOrA(BigDecimal target, BigDecimal eaten) {
        if (target == null) return null;
        return target.subtract(eaten == null ? BigDecimal.ZERO : eaten);
    }

    /**
     * Scale every component to 2 decimal places HALF_UP so the wire
     * payload matches DB precision. {@link Optional}-style null handling.
     */
    private static Macros round(Macros m) {
        if (m == null) return null;
        return new Macros(
                scale(m.kcal()), scale(m.proteinG()), scale(m.carbG()),
                scale(m.fatG()), scale(m.fiberG()), scale(m.sodiumMg()));
    }

    private static BigDecimal scale(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }
}
