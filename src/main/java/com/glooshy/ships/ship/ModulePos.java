package com.glooshy.ships.ship;

import java.util.Comparator;
import java.util.Objects;

/**
 * One discrete module position on a ship's hull grid (RQCA-08): column 0..w-1
 * counted left→right (port→starboard), row 0..l-1 counted bow→stern. The set
 * of valid positions depends on the ship size (see {@link ShipSize}).
 *
 * <p>Encoded form "r&lt;row&gt;c&lt;col&gt;" is used in persistence and PDC markers.
 */
public record ModulePos(int col, int row) implements Comparable<ModulePos> {

    private static final Comparator<ModulePos> ORDER =
            Comparator.comparingInt(ModulePos::row).thenComparingInt(ModulePos::col);

    public ModulePos {
        if (col < 0 || row < 0) {
            throw new IllegalArgumentException("ModulePos must be non-negative: c" + col + " r" + row);
        }
    }

    public String encoded() {
        return "r" + row + "c" + col;
    }

    /** Parse "r&lt;row&gt;c&lt;col&gt;"; returns null when malformed. */
    public static ModulePos decode(String encoded) {
        if (encoded == null || encoded.length() < 4 || encoded.charAt(0) != 'r') {
            return null;
        }
        int c = encoded.indexOf('c', 1);
        if (c < 0) {
            return null;
        }
        try {
            return new ModulePos(
                    Integer.parseInt(encoded.substring(c + 1)),
                    Integer.parseInt(encoded.substring(1, c)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Ship-local offset in blocks: x starboard-positive, z forward-positive. */
    public double localX(ShipSize size) {
        return (col - (size.width() - 1) / 2.0);
    }

    public double localZ(ShipSize size) {
        return ((size.length() - 1) / 2.0 - row);
    }

    @Override
    public int compareTo(ModulePos other) {
        return ORDER.compare(this, other);
    }

    /**
     * Rotate a ship-local offset into world coordinates for the given yaw
     * (Minecraft convention: yaw 0 → +Z, yaw 90 → −X, increasing clockwise
     * viewed from above).
     *
     * <p>Forward vector F = (−sin y, cos y); starboard (right) vector
     * R = (−cos y, −sin y). World offset = R·localX + F·localZ.
     *
     * @return {@code [dx, dz]} world-space offset
     */
    public static double[] worldOffset(double yawDegrees, double localX, double localZ) {
        double yawRad = Math.toRadians(yawDegrees);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);
        double dx = -cos * localX - sin * localZ;
        double dz = -sin * localX + cos * localZ;
        return new double[] {dx, dz};
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ModulePos p && p.col == col && p.row == row;
    }

    @Override
    public int hashCode() {
        return Objects.hash(col, row);
    }
}
