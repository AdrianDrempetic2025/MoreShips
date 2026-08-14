package com.glooshy.ships.ship;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometry of a ship's hull footprint (spec L1 §2): the hitbox should
 * match the hull dimensions — 2×3, 3×4, 3×8 — slightly inset so ships fit
 * into docks without pixel-perfect placement.
 *
 * <p>A rectangle is approximated by a line of square interaction segments
 * along the hull axis; solidity is a w×l grid of 1×1 cells.
 */
public final class HullShape {

    private HullShape() {
    }

    /** Square interaction segment side ≈ hull width, slightly inset. */
    public static double segmentSize(ShipSize size) {
        return size.width() * 0.98;
    }

    /**
     * Local Z centers (forward-positive) of the interaction segments, evenly
     * spread so their union spans the hull length.
     */
    public static List<Double> segmentCentersZ(ShipSize size) {
        double l = size.length();
        double s = segmentSize(size);
        // -0.3 slack: a medium hull (4 long, ~3-wide segments) still gets 2
        // segments instead of one under-covering square
        int n = Math.max(1, (int) Math.ceil(l / s - 0.3));
        List<Double> centers = new ArrayList<>(n);
        double first = -l / 2.0 + s / 2.0;
        double last = l / 2.0 - s / 2.0;
        for (int i = 0; i < n; i++) {
            centers.add(n == 1 ? 0.0 : first + (last - first) * i / (n - 1));
        }
        return centers;
    }

    /** One solidity cell of the w×l deck grid. */
    public record Cell(double localX, double localZ) {
    }

    /**
     * Local positions of the 1×1 solidity cells covering the hull. Cells within
     * {@link #DECK_CENTER_CLEARENCE} of the center are skipped: the controller
     * stand and the pilot sit there, and a solid cell would block the ship's
     * own movement (bug: "the ship isn't able to move").
     */
    public static List<Cell> solidCells(ShipSize size) {
        List<Cell> cells = new ArrayList<>(size.width() * size.length());
        for (int col = 0; col < size.width(); col++) {
            for (int row = 0; row < size.length(); row++) {
                double x = col - (size.width() - 1) / 2.0;
                double z = (size.length() - 1) / 2.0 - row;
                if (Math.hypot(x, z) < DECK_CENTER_CLEARENCE) {
                    continue;
                }
                cells.add(new Cell(x, z));
            }
        }
        return cells;
    }

    /** Radius around the ship center kept free of solidity cells. */
    public static final double DECK_CENTER_CLEARENCE = 1.1;

    /** The deck cell directly under the controller stand. The stand itself is
     *  non-collidable and teleports, so this cell is safe — it closes the
     *  center hole in the solid deck. */
    public static Cell centerCell() {
        return new Cell(0.0, 0.0);
    }

    /**
     * The union of interaction segments must cover the hull footprint within
     * a small tolerance (used by tests).
     */
    public static double coverageLength(ShipSize size) {
        List<Double> centers = segmentCentersZ(size);
        double s = segmentSize(size);
        double min = centers.get(0) - s / 2.0;
        double max = centers.get(centers.size() - 1) + s / 2.0;
        return max - min;
    }
}
