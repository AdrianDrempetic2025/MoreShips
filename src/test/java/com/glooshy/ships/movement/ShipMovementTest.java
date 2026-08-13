package com.glooshy.ships.movement;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * FALSIFICATION_PROOF + REGRESSION_GUARDs for {@link ShipMovement}.
 *
 * <p>Named defect: DEFECT-14 (NO_ACCELERATION) — the ship jumps to max speed
 * instantly (no acceleration phase) or never moves at all. The acceleration
 * invariant is the L5-04 RQCA-14 requirement: "speed at t≈0 ≠ max speed".
 */
class ShipMovementTest {

    /**
     * FALSIFICATION_PROOF — DEFECT-14.
     *
     * <p>Mutation plans that must RED:
     * <ul>
     *   <li>tick() returns without modifying currentSpeed</li>
     *   <li>engage() sets currentSpeed = maxSpeed directly</li>
     *   <li>tick() uses friction instead of acceleration when speeding up</li>
     * </ul>
     */
    @Test
    void engage_then_tick_accelerates_progressively() {
        ShipMovement m = new ShipMovement(0.4, 0.02, 0.05);

        // At t=0: stationary
        assertEquals(0.0, m.currentSpeed(), "ship starts stationary");

        m.engage();
        m.tick();
        assertEquals(0.02, m.currentSpeed(), 1e-9, "after 1 tick: 0 + accel = 0.02");

        m.tick();
        assertEquals(0.04, m.currentSpeed(), 1e-9, "after 2 ticks: 0.04");

        m.tick();
        assertEquals(0.06, m.currentSpeed(), 1e-9, "after 3 ticks: 0.06");
    }

    /**
     * Regression — speed at t≈0 ≠ max speed (RQCA-14).
     *
     * <p>This is the exact invariant the spec requires. The first non-zero
     * tick must NOT equal maxSpeed.
     */
    @Test
    void speed_at_t1_not_equal_to_max_speed() {
        ShipMovement m = new ShipMovement(0.4, 0.02, 0.05);
        m.engage();
        m.tick();

        assertNotEquals(0.4, m.currentSpeed(), "RQCA-14: first-tick speed ≠ max speed");
        assertTrue(m.currentSpeed() < 0.4, "speed must be below max during accel phase");
    }

    @Test
    void engage_reaches_max_speed_in_expected_ticks() {
        ShipMovement m = new ShipMovement(0.4, 0.02, 0.05);
        m.engage();

        // 0.4 / 0.02 = 20 ticks to reach max from 0
        for (int i = 0; i < 20; i++) {
            m.tick();
        }
        assertEquals(0.4, m.currentSpeed(), 1e-9, "after 20 ticks at accel 0.02, speed = 0.4");
    }

    @Test
    void current_speed_never_exceeds_max() {
        ShipMovement m = new ShipMovement(0.4, 0.02, 0.05);
        m.engage();
        for (int i = 0; i < 1000; i++) {
            m.tick();
        }
        assertEquals(0.4, m.currentSpeed(), 1e-9, "no overrun after 1000 ticks");
    }

    @Test
    void tick_without_engage_stays_at_zero() {
        ShipMovement m = new ShipMovement(0.4, 0.02, 0.05);
        for (int i = 0; i < 100; i++) {
            m.tick();
        }
        assertEquals(0.0, m.currentSpeed(), 1e-9, "no engage → no movement");
        assertFalse(m.isMoving(), "isMoving false when stationary");
    }

    @Test
    void disengage_decelerates_via_friction() {
        ShipMovement m = new ShipMovement(0.4, 0.02, 0.05);
        m.engage();
        // Reach max speed
        for (int i = 0; i < 20; i++) m.tick();
        assertEquals(0.4, m.currentSpeed(), 1e-9, "precondition: at max");

        m.disengage();
        m.tick();
        assertEquals(0.35, m.currentSpeed(), 1e-9, "first disengage tick: 0.4 - 0.05 = 0.35");

        m.tick();
        assertEquals(0.30, m.currentSpeed(), 1e-9, "second: 0.30");

        // Drift to zero (0.4 / 0.05 = 8 ticks)
        for (int i = 0; i < 6; i++) m.tick();
        assertEquals(0.0, m.currentSpeed(), 1e-9, "after 8 total disengage ticks, stopped");
        assertFalse(m.isMoving());
    }

    @Test
    void friction_uses_friction_not_acceleration() {
        // If tick uses acceleration instead of friction when slowing,
        // decel from 0.4 with accel 0.02 takes 20 ticks, not 8.
        ShipMovement m = new ShipMovement(0.4, 0.02, 0.05);
        m.engage();
        for (int i = 0; i < 20; i++) m.tick();
        m.disengage();
        for (int i = 0; i < 8; i++) m.tick();
        assertEquals(0.0, m.currentSpeed(), 1e-9,
                "friction (0.05) must be used when decelerating, not acceleration (0.02)");
    }

    @Test
    void re_engage_after_partial_deceleration() {
        ShipMovement m = new ShipMovement(0.4, 0.02, 0.05);
        m.engage();
        for (int i = 0; i < 20; i++) m.tick();  // at max
        m.disengage();
        for (int i = 0; i < 4; i++) m.tick();    // down to 0.20

        m.engage();
        m.tick();
        assertEquals(0.22, m.currentSpeed(), 1e-9,
                "re-engage resumes acceleration from current speed");
    }

    @Test
    void isMoving_threshold() {
        ShipMovement m = new ShipMovement(0.4, 0.02, 0.05);
        assertFalse(m.isMoving(), "stationary ship isMoving=false");

        m.engage();
        m.tick();
        assertTrue(m.isMoving(), "moving ship isMoving=true");
    }

    @Test
    void invalid_config_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ShipMovement(-0.1, 0.02, 0.05),
                "negative maxSpeed rejected");
        assertThrows(IllegalArgumentException.class,
                () -> new ShipMovement(0.4, 0.0, 0.05),
                "zero acceleration rejected");
        assertThrows(IllegalArgumentException.class,
                () -> new ShipMovement(0.4, 0.02, 0.0),
                "zero friction rejected");
        assertThrows(IllegalArgumentException.class,
                () -> new ShipMovement(0.4, -1.0, 0.05),
                "negative acceleration rejected");
    }

    @Test
    void zero_max_speed_is_valid_and_immobile() {
        // maxSpeed=0 is allowed (server owner disables movement)
        ShipMovement m = new ShipMovement(0.0, 0.02, 0.05);
        m.engage();
        for (int i = 0; i < 10; i++) m.tick();
        assertEquals(0.0, m.currentSpeed(), 1e-9, "maxSpeed=0 → never moves");
        assertFalse(m.isMoving());
    }
}
