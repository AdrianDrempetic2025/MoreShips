package com.glooshy.ships.ship;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for the hull footprint geometry: the interaction segments must cover
 * the hull rectangle (2×3, 3×4, 3×8) within a small tolerance, and the
 * solidity grid must tile it exactly.
 */
class HullShapeTest {

    @Test
    void segment_coverage_matches_hull_length_within_tolerance() {
        assertCoverage(ShipSize.SMALL, 3.0);
        assertCoverage(ShipSize.MEDIUM, 4.0);
        assertCoverage(ShipSize.LARGE, 8.0);
    }

    private void assertCoverage(ShipSize size, double expectedLength) {
        double coverage = HullShape.coverageLength(size);
        assertTrue(Math.abs(coverage - expectedLength) <= 0.5,
                size + ": coverage " + coverage + " should be ~" + expectedLength);
        assertTrue(coverage <= expectedLength + 0.5,
                size + ": coverage must not overshoot the hull by much");
    }

    @Test
    void segment_size_matches_hull_width() {
        for (ShipSize size : ShipSize.values()) {
            assertTrue(HullShape.segmentSize(size) <= size.width(),
                    size + ": segments must not be wider than the hull");
            assertTrue(HullShape.segmentSize(size) > size.width() - 0.2,
                    size + ": segments should span (almost) the full width");
        }
    }

    @Test
    void solid_grid_tiles_the_hull_except_center_clearance() {
        for (ShipSize size : ShipSize.values()) {
            long expected = size.width() * size.length() - HullShape.solidCells(size).size();
            assertTrue(expected >= 0);
            assertEquals(expected,
                    countWithinClearance(size),
                    size + ": only center cells may be skipped");
            double minX = HullShape.solidCells(size).stream()
                    .mapToDouble(HullShape.Cell::localX).min().orElseThrow();
            double maxX = HullShape.solidCells(size).stream()
                    .mapToDouble(HullShape.Cell::localX).max().orElseThrow();
            double minZ = HullShape.solidCells(size).stream()
                    .mapToDouble(HullShape.Cell::localZ).min().orElseThrow();
            double maxZ = HullShape.solidCells(size).stream()
                    .mapToDouble(HullShape.Cell::localZ).max().orElseThrow();
            assertEquals(size.width() - 1.0, maxX - minX, 1e-9, size + " width span");
            assertEquals(size.length() - 1.0, maxZ - minZ, 1e-9, size + " length span");
        }
    }

    private long countWithinClearance(ShipSize size) {
        // recompute skipped cells the same way solidCells does
        int count = 0;
        for (int col = 0; col < size.width(); col++) {
            for (int row = 0; row < size.length(); row++) {
                double x = col - (size.width() - 1) / 2.0;
                double z = (size.length() - 1) / 2.0 - row;
                if (Math.hypot(x, z) < HullShape.DECK_CENTER_CLEARENCE) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    void center_clearance_keeps_controller_free() {
        for (ShipSize size : ShipSize.values()) {
            for (HullShape.Cell cell : HullShape.solidCells(size)) {
                assertTrue(Math.hypot(cell.localX(), cell.localZ())
                                >= HullShape.DECK_CENTER_CLEARENCE,
                        size + ": no cell may sit in the center clearance");
            }
        }
    }

    @Test
    void bow_cell_is_forward_stern_cell_is_behind() {
        HullShape.Cell bow = HullShape.solidCells(ShipSize.MEDIUM).stream()
                .max(java.util.Comparator.comparingDouble(HullShape.Cell::localZ)).orElseThrow();
        HullShape.Cell stern = HullShape.solidCells(ShipSize.MEDIUM).stream()
                .min(java.util.Comparator.comparingDouble(HullShape.Cell::localZ)).orElseThrow();
        assertTrue(bow.localZ() > 0);
        assertTrue(stern.localZ() < 0);
    }
}
