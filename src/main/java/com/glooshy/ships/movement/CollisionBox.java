package com.glooshy.ships.movement;

/**
 * Pure AABB collision math for the ship hull (spec L1 §2: the collision box
 * is slightly smaller than the visual dimensions so ships fit into
 * appropriately sized spaces without pixel-perfect placement).
 *
 * <p>The ship is modeled as a box centered on the controller entity's X/Z,
 * rising from its Y. Given a proposed movement delta, callers ask for the
 * axis-clamped delta that avoids solid blocks.
 */
public final class CollisionBox {

    private final double width;
    private final double height;
    private final double margin;

    public CollisionBox(double width, double height, double margin) {
        this.width = width;
        this.height = height;
        this.margin = margin;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    /** Block-space X range of the box placed at centerX with a delta. */
    public int[] blockRangeX(double centerX, double dx) {
        double half = Math.max(0.05, width / 2.0 - margin);
        return range(centerX + dx - half, centerX + dx + half);
    }

    /** Block-space Y range of the box placed at baseY. */
    public int[] blockRangeY(double baseY) {
        return range(baseY, baseY + height);
    }

    /** Block-space Z range of the box placed at centerZ with a delta. */
    public int[] blockRangeZ(double centerZ, double dz) {
        double half = Math.max(0.05, width / 2.0 - margin);
        return range(centerZ + dz - half, centerZ + dz + half);
    }

    private static int[] range(double min, double max) {
        return new int[] {(int) Math.floor(min), (int) Math.floor(max - 1e-9)};
    }

    /**
     * Axis-clamp a proposed movement: if the full (dx, dz) step collides,
     * try each axis separately (slide along walls); if both are blocked,
     * stop completely.
     *
     * @param collidesAt tester: does the box at (x+dx, z+dz) hit a solid block?
     * @return the clamped {@code [dx, dz]}
     */
    public double[] clampMovement(
            double x, double z, double dx, double dz, PositionCollisionTest collidesAt) {
        if (dx == 0.0 && dz == 0.0) {
            return new double[] {0.0, 0.0};
        }
        if (!collidesAt.test(x + dx, z + dz)) {
            return new double[] {dx, dz};
        }
        if (dx != 0.0 && !collidesAt.test(x + dx, z)) {
            return new double[] {dx, 0.0};
        }
        if (dz != 0.0 && !collidesAt.test(x, z + dz)) {
            return new double[] {0.0, dz};
        }
        return new double[] {0.0, 0.0};
    }

    @FunctionalInterface
    public interface PositionCollisionTest {
        boolean test(double x, double z);
    }
}
