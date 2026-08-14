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
    void solid_grid_tiles_the_hull_exactly() {
        for (ShipSize size : ShipSize.values()) {
            assertEquals(size.width() * size.length(),
                    HullShape.solidCells(size).size(),
                    size + ": one cell per hull block");
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

    @Test
    void bow_cell_is_forward_stern_cell_is_behind() {
        HullShape.Cell bow = new HullShape.Cell(0, 0);
        HullShape.Cell stern = new HullShape.Cell(0, ShipSize.MEDIUM.length() - 1);
        assertTrue(bow.localZ() > 0);
        assertTrue(stern.localZ() < 0);
    }
}
