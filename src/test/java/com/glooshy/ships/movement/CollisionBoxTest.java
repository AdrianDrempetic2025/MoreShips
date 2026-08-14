package com.glooshy.ships.movement;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for the hull collision math ({@link CollisionBox}).
 *
 * <p>Uses a synthetic collision test: a wall at x >= 10 blocks everything
 * with a proposed x >= 9 (so the box's right edge would cross into it).
 */
class CollisionBoxTest {

    // Hull 3 wide with 0.15 margin → half-extent 1.35: box at x spans
    // [x-1.35, x+1.35]. Wall occupies block x=10 ([10.0, 11.0)).
    private final CollisionBox box = new CollisionBox(3.0, 1.8, 0.15);

    private boolean wallAt(double boxCenterX, double boxCenterZ) {
        double half = 3.0 / 2.0 - 0.15;
        return boxCenterX + half >= 10.0;
    }

    @Test
    void free_movement_passes_unchanged() {
        double[] moved = box.clampMovement(5.0, 5.0, 0.4, 0.4, this::wallAt);
        assertArrayEquals(new double[] {0.4, 0.4}, moved, 1e-9);
    }

    @Test
    void diagonal_into_wall_slides_along_it() {
        // At x=8.5 the box (right edge 9.85) is clear of the wall; moving +0.4
        // in x would cross it (10.25), but z alone stays clear → slide on z
        double[] moved = box.clampMovement(8.5, 5.0, 0.4, 0.4, this::wallAt);
        assertArrayEquals(new double[] {0.0, 0.4}, moved, 1e-9);
    }

    @Test
    void head_on_wall_stops_completely() {
        double[] moved = box.clampMovement(8.5, 5.0, 0.4, 0.0, this::wallAt);
        assertArrayEquals(new double[] {0.0, 0.0}, moved, 1e-9);
    }

    @Test
    void zero_movement_is_free() {
        double[] moved = box.clampMovement(9.5, 5.0, 0.0, 0.0, this::wallAt);
        assertArrayEquals(new double[] {0.0, 0.0}, moved, 1e-9);
    }

    @Test
    void block_ranges_cover_box_extent() {
        // Box centered at x=5.0 spans [3.65, 6.35] → blocks 3..6
        int[] xs = box.blockRangeX(5.0, 0.0);
        assertEquals(3, xs[0]);
        assertEquals(6, xs[1]);
    }

    @Test
    void block_range_y_covers_height() {
        int[] ys = box.blockRangeY(64.0);
        assertEquals(64, ys[0]);
        assertEquals(65, ys[1]);
    }
}
