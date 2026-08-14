package com.glooshy.ships.ship;

/**
 * Discrete module positions on a ship (RQCA-08: "occupying discrete
 * positions").
 *
 * <p>Each slot is a ship-local offset: {@code localZ} is forward (along the
 * ship's facing), {@code localX} is starboard (right side). The ship entity
 * is the controller; module entities hold these positions and follow the ship
 * every tick (see {@link ModuleSlot#worldOffset(double, double, double)}).
 */
public enum ModuleSlot {
    BOW(0.0, 1.9),
    STERN(0.0, -1.9),
    PORT(-1.5, 0.0),
    STARBOARD(1.5, 0.0);

    /** Vertical offset of the module entity above the ship entity. */
    public static final double Y_OFFSET = 0.35;

    private final double localX;
    private final double localZ;

    ModuleSlot(double localX, double localZ) {
        this.localX = localX;
        this.localZ = localZ;
    }

    public double localX() {
        return localX;
    }

    public double localZ() {
        return localZ;
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
}
