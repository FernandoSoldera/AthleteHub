-- AH-051: Seed the global food catalog with staples — enough breadth to
-- plan a basic diet without forcing the user to create customs on day
-- one. Macros are per 100 g (serving_size_g = 100) so the diet calculator
-- can scale linearly. Values come from USDA averages, rounded to 1
-- decimal place. Brand stays NULL — these are generic foods.
--
-- Custom foods (added via POST /api/foods) land alongside these with
-- is_global = false and created_by = the owning user.

INSERT INTO foods (name, is_global, serving_size_g, kcal, protein_g, carb_g, fat_g, fiber_g, sodium_mg) VALUES
    -- ── proteins ──────────────────────────────────────────────────────
    ('Chicken Breast (cooked)',   true, 100, 165, 31.0,  0.0,  3.6, 0,   74),
    ('Lean Beef (cooked)',        true, 100, 250, 26.0,  0.0, 15.0, 0,   72),
    ('Salmon (cooked)',           true, 100, 208, 20.0,  0.0, 13.0, 0,   59),
    ('Tuna (canned in water)',    true, 100, 116, 26.0,  0.0,  1.0, 0,  247),
    ('Egg (whole)',               true, 100, 155, 13.0,  1.1, 11.0, 0,  124),
    ('Egg White',                 true, 100,  52, 11.0,  0.7,  0.2, 0,  166),
    ('Greek Yogurt (plain)',      true, 100,  59, 10.0,  3.6,  0.4, 0,   36),
    ('Cottage Cheese',            true, 100,  98, 11.0,  3.4,  4.3, 0,  364),
    ('Whey Protein Powder',       true, 100, 370, 80.0,  5.0,  4.0, 0,  150),

    -- ── dairy ─────────────────────────────────────────────────────────
    ('Milk (whole)',              true, 100,  61,  3.2,  4.8,  3.3, 0,   43),
    ('Milk (skim)',               true, 100,  34,  3.4,  5.0,  0.1, 0,   42),

    -- ── carbs / grains ────────────────────────────────────────────────
    ('White Rice (cooked)',       true, 100, 130,  2.7, 28.0,  0.3, 0.4,  1),
    ('Brown Rice (cooked)',       true, 100, 112,  2.6, 23.0,  0.9, 1.8,  5),
    ('Oats (dry)',                true, 100, 389, 16.9, 66.0,  6.9, 10.6, 2),
    ('Pasta (cooked)',            true, 100, 131,  5.0, 25.0,  1.1, 1.8,  6),
    ('Bread (whole wheat)',       true, 100, 247, 13.0, 41.0,  3.4, 7.0, 472),
    ('Sweet Potato (cooked)',     true, 100,  86,  1.6, 20.0,  0.1, 3.0, 55),
    ('Potato (boiled)',           true, 100,  87,  1.9, 20.0,  0.1, 1.8,  4),

    -- ── fruit ─────────────────────────────────────────────────────────
    ('Banana',                    true, 100,  89,  1.1, 23.0,  0.3, 2.6,  1),
    ('Apple',                     true, 100,  52,  0.3, 14.0,  0.2, 2.4,  1),
    ('Orange',                    true, 100,  47,  0.9, 12.0,  0.1, 2.4,  0),
    ('Berries (mixed)',           true, 100,  57,  0.7, 14.0,  0.3, 5.0,  1),

    -- ── vegetables ────────────────────────────────────────────────────
    ('Broccoli',                  true, 100,  34,  2.8,  7.0,  0.4, 2.6, 33),
    ('Spinach',                   true, 100,  23,  2.9,  3.6,  0.4, 2.2, 79),

    -- ── fats ──────────────────────────────────────────────────────────
    ('Olive Oil',                 true, 100, 884,  0.0,  0.0,100.0, 0,    2),
    ('Peanut Butter',             true, 100, 588, 25.0, 20.0, 50.0, 6.0, 17),
    ('Almonds',                   true, 100, 579, 21.0, 22.0, 50.0,12.5,  1);
