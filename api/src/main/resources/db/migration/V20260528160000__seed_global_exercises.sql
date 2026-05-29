-- AH-031: Seed the global exercise catalog. These are the "starter" lifts
-- every gym has — enough breadth to plan push/pull/leg sessions without
-- forcing the user to create customs on day one. Custom exercises (added
-- via POST /api/exercises) land alongside these with is_global = false
-- and created_by = the owning user.
--
-- Categories: push / pull / legs / core. Equipment values stay short
-- (barbell, dumbbell, cable, machine, bodyweight) so the client can
-- group them without a translation map. The seed deliberately avoids
-- niche or brand-specific lifts ("Smith squat", "Hammer Strength row")
-- — those belong in user customs.

INSERT INTO exercises (name, category, primary_muscle, equipment, is_global) VALUES
    -- ── push ─────────────────────────────────────────────────────────
    ('Bench Press',              'push', 'chest',     'barbell',    true),
    ('Incline Bench Press',      'push', 'chest',     'barbell',    true),
    ('Dumbbell Bench Press',     'push', 'chest',     'dumbbell',   true),
    ('Overhead Press',           'push', 'shoulders', 'barbell',    true),
    ('Dumbbell Shoulder Press',  'push', 'shoulders', 'dumbbell',   true),
    ('Lateral Raise',            'push', 'shoulders', 'dumbbell',   true),
    ('Push-Up',                  'push', 'chest',     'bodyweight', true),
    ('Dip',                      'push', 'chest',     'bodyweight', true),
    ('Triceps Pushdown',         'push', 'triceps',   'cable',      true),
    ('Skull Crusher',            'push', 'triceps',   'barbell',    true),

    -- ── pull ─────────────────────────────────────────────────────────
    ('Deadlift',                 'pull', 'back',      'barbell',    true),
    ('Barbell Row',              'pull', 'back',      'barbell',    true),
    ('Dumbbell Row',             'pull', 'back',      'dumbbell',   true),
    ('Pull-Up',                  'pull', 'back',      'bodyweight', true),
    ('Chin-Up',                  'pull', 'back',      'bodyweight', true),
    ('Lat Pulldown',             'pull', 'back',      'cable',      true),
    ('Seated Cable Row',         'pull', 'back',      'cable',      true),
    ('Face Pull',                'pull', 'shoulders', 'cable',      true),
    ('Barbell Curl',             'pull', 'biceps',    'barbell',    true),
    ('Dumbbell Curl',            'pull', 'biceps',    'dumbbell',   true),

    -- ── legs ─────────────────────────────────────────────────────────
    ('Back Squat',               'legs', 'quads',     'barbell',    true),
    ('Front Squat',              'legs', 'quads',     'barbell',    true),
    ('Romanian Deadlift',        'legs', 'hamstrings','barbell',    true),
    ('Leg Press',                'legs', 'quads',     'machine',    true),
    ('Leg Curl',                 'legs', 'hamstrings','machine',    true),
    ('Leg Extension',            'legs', 'quads',     'machine',    true),
    ('Lunge',                    'legs', 'quads',     'dumbbell',   true),
    ('Bulgarian Split Squat',    'legs', 'quads',     'dumbbell',   true),
    ('Hip Thrust',               'legs', 'glutes',    'barbell',    true),
    ('Calf Raise',               'legs', 'calves',    'machine',    true),

    -- ── core ─────────────────────────────────────────────────────────
    ('Plank',                    'core', 'core',      'bodyweight', true),
    ('Hanging Leg Raise',        'core', 'core',      'bodyweight', true),
    ('Ab Wheel Rollout',         'core', 'core',      'bodyweight', true),
    ('Cable Crunch',             'core', 'core',      'cable',      true);
