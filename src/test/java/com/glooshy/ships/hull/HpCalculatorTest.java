package com.glooshy.ships.hull;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * FALSIFICATION_PROOF + REGRESSION_GUARDs for {@link HpCalculator}.
 *
 * <p>Named defect: DEFECT-12 (HP_MISCOMPUTED) — the formula doesn't actually
 * use the material's hardness (or the multiplier), so HP is wrong.
 */
class HpCalculatorTest {

    /**
     * FALSIFICATION_PROOF — DEFECT-12.
     *
     * <p>Mutation plan: replace {@code computeMaxHp} body with a constant
     * (e.g., {@code return 100;}). Expected RED: every assertion that depends
     * on hardness or multiplier fails.
     */
    @Test
    void computeMaxHp_scales_with_hardness() {
        HpCalculator calc = new HpCalculator(10.0);

        assertEquals(15, calc.computeMaxHp(1.5), "Stone (1.5) → 15 HP");
        assertEquals(20, calc.computeMaxHp(2.0), "Planks (2.0) → 20 HP");
        assertEquals(500, calc.computeMaxHp(50.0), "Obsidian (50) → 500 HP");
        assertEquals(60, calc.computeMaxHp(6.0), "Iron block (6.0) → 60 HP");
    }

    @Test
    void computeMaxHp_respects_multiplier() {
        HpCalculator strict = new HpCalculator(100.0);
        assertEquals(150, strict.computeMaxHp(1.5), "Stone at multiplier 100 → 150 HP");

        HpCalculator lenient = new HpCalculator(1.0);
        assertEquals(2, lenient.computeMaxHp(1.5), "Stone at multiplier 1 → 2 HP (rounded)");
    }

    @Test
    void computeMaxHp_clamps_to_minimum_one() {
        HpCalculator calc = new HpCalculator(1.0);
        assertEquals(1, calc.computeMaxHp(0.0), "Zero hardness clamps to 1 HP");
        assertEquals(1, calc.computeMaxHp(0.3), "0.3 hardness rounds to 0, clamps to 1");
    }

    @Test
    void zero_multiplier_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new HpCalculator(0.0));
        assertThrows(IllegalArgumentException.class, () -> new HpCalculator(-1.0));
    }

    @Test
    void rounds_to_nearest_integer() {
        HpCalculator calc = new HpCalculator(10.0);
        assertEquals(15, calc.computeMaxHp(1.5), "1.5 × 10 = 15.0 → 15");
        assertEquals(16, calc.computeMaxHp(1.55), "1.55 × 10 = 15.5 → 16 (round half up)");
        assertEquals(15, calc.computeMaxHp(1.49), "1.49 × 10 = 14.9 → 15");
    }
}
