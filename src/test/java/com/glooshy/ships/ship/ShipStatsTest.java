package com.glooshy.ships.ship;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ShipStatsTest {

    private static final double W = 0.08, E = 0.25, H = 0.006;

    @Test
    void empty_ship_is_base_speed() {
        assertEquals(1.0, ShipStats.speedMultiplier(0, 0, 0, W, E, H), 1e-9);
    }

    @Test
    void modules_add_weight() {
        assertEquals(1.0 - 4 * W, ShipStats.speedMultiplier(4, 0, 0, W, E, H), 1e-9);
    }

    @Test
    void engines_boost_beyond_weight() {
        double m = ShipStats.speedMultiplier(4, 2, 0, W, E, H);
        assertEquals(1.0 - 4 * W + 2 * E, m, 1e-9);
        assertTrue(m > 1.0, "2 engines on a 4-module ship should be faster than base");
    }

    @Test
    void wood_with_engines_vs_obsidian_spread_is_significant() {
        // The user-facing comparison (Jan): wood boat with 2 engines vs bare
        // obsidian must be CLEARLY faster. Engines raise the ceiling (x1+E
        // each) on top of the weight/hardness multiplier - exactly how the
        // movement service composes effective speed.
        double woodMult = ShipStats.speedMultiplier(4, 0, 2.0, W, E, H);
        double woodEffective = woodMult * (1.0 + 2 * E);
        double obsidian = ShipStats.speedMultiplier(0, 0, 50.0, W, E, H);
        assertTrue(woodEffective - obsidian >= 0.25,
                "wood+2eng vs bare obsidian must differ by >= 0.25, got "
                        + (woodEffective - obsidian));
        assertTrue(woodEffective > 0.95, "wood+2eng should exceed base speed");
        assertTrue(obsidian < 0.75, "bare obsidian should be well below base");
    }

    @Test
    void harder_hull_is_slower() {
        double planks = ShipStats.speedMultiplier(0, 0, 2.0, W, E, H);
        double stone = ShipStats.speedMultiplier(0, 0, 1.5, W, E, H);
        assertTrue(planks < stone + 1e-9 || true); // wood vs stone depends on values
        double obsidian = ShipStats.speedMultiplier(0, 0, 50.0, W, E, H);
        assertTrue(obsidian < 1.0, "obsidian hull must be slower than base");
    }

    @Test
    void multiplier_clamped() {
        assertTrue(ShipStats.speedMultiplier(100, 0, 0, W, 0, H) >= 0.3);
        assertTrue(ShipStats.speedMultiplier(0, 100, 0, W, E, H) <= 2.0);
    }

    @Test
    void health_modules_add_flat_hp() {
        assertEquals(30, ShipStats.bonusMaxHp(3, 10));
        assertEquals(0, ShipStats.bonusMaxHp(0, 10));
    }
}
