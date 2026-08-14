package com.glooshy.ships.ship;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for the ship-local → world offset rotation math
 * ({@link ModuleSlot#worldOffset}).
 *
 * <p>Minecraft yaw convention: 0 → +Z (south), 90 → −X (west), increasing
 * clockwise viewed from above. Forward F = (−sin y, cos y); starboard R =
 * (−cos y, −sin y).
 */
class ModuleSlotTest {

    private static final double EPS = 1e-9;

    @Test
    void yaw0_forward_is_south() {
        double[] o = ModuleSlot.worldOffset(0, 0, 1);
        assertEquals(0, o[0], EPS);
        assertEquals(1, o[1], EPS);
    }

    @Test
    void yaw0_starboard_is_west() {
        double[] o = ModuleSlot.worldOffset(0, 1, 0);
        assertEquals(-1, o[0], EPS);
        assertEquals(0, o[1], EPS);
    }

    @Test
    void yaw90_forward_is_west() {
        double[] o = ModuleSlot.worldOffset(90, 0, 1);
        assertEquals(-1, o[0], EPS);
        assertEquals(0, o[1], EPS);
    }

    @Test
    void yaw90_starboard_is_north() {
        double[] o = ModuleSlot.worldOffset(90, 1, 0);
        assertEquals(0, o[0], EPS);
        assertEquals(-1, o[1], EPS);
    }

    @Test
    void yaw180_forward_is_north() {
        double[] o = ModuleSlot.worldOffset(180, 0, 1);
        assertEquals(0, o[0], EPS);
        assertEquals(-1, o[1], EPS);
    }

    @Test
    void rotation_preserves_offset_length() {
        for (double yaw : new double[] {0, 33, 90, 137, 180, 245, 311, 359}) {
            double[] o = ModuleSlot.worldOffset(yaw, 1.5, 1.9);
            double length = Math.sqrt(o[0] * o[0] + o[1] * o[1]);
            assertEquals(Math.sqrt(1.5 * 1.5 + 1.9 * 1.9), length, EPS,
                    "Rotation must not stretch offsets (yaw=" + yaw + ")");
        }
    }

    @Test
    void bow_stays_in_front_under_full_turn() {
        // BOW offset at increasing yaw must trace a circle, never flip sides
        double[] first = ModuleSlot.worldOffset(0, ModuleSlot.BOW.localX(), ModuleSlot.BOW.localZ());
        double[] turned = ModuleSlot.worldOffset(180, ModuleSlot.BOW.localX(), ModuleSlot.BOW.localZ());
        assertEquals(-first[0], turned[0], EPS);
        assertEquals(-first[1], turned[1], EPS);
    }
}
