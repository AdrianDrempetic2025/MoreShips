package com.glooshy.ships.movement;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for the vertical water-physics decision ({@link WaterPhysics}).
 *
 * <p>Regimes: fully submerged → rise; at surface → hold; out of water → fall.
 */
class WaterPhysicsTest {

    private final WaterPhysics physics = new WaterPhysics(0.35, -0.30);

    @Test
    void fully_submerged_ship_rises() {
        assertEquals(0.35, physics.verticalVelocity(true, true), 1e-9);
    }

    @Test
    void ship_at_surface_holds_position() {
        assertEquals(0.0, physics.verticalVelocity(true, false), 1e-9);
    }

    @Test
    void ship_out_of_water_falls() {
        assertEquals(-0.30, physics.verticalVelocity(false, false), 1e-9);
    }

    @Test
    void ship_in_air_with_water_above_falls() {
        // Weird edge (waterfall above, air at feet): no buoyancy support
        assertEquals(-0.30, physics.verticalVelocity(false, true), 1e-9);
    }

    @Test
    void defaults_match_expected_tuning() {
        WaterPhysics defaults = WaterPhysics.defaults();
        assertEquals(0.35, defaults.riseVelocity(), 1e-9);
        assertEquals(-0.30, defaults.sinkVelocity(), 1e-9);
    }
}
