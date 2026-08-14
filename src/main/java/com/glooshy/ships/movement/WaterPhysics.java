package com.glooshy.ships.movement;

/**
 * Pure vertical-physics decision for a ship hull (RQCA-14 adjacent: ships sit
 * IN the world, not above it).
 *
 * <p>Three regimes, decided from two water probes at the ship's position:
 * <ul>
 *   <li><b>Fully submerged</b> (water at feet AND above) → rise</li>
 *   <li><b>At the surface</b> (water at feet, air above) → hold position</li>
 *   <li><b>Airborne / beached</b> (no water at feet) → fall</li>
 * </ul>
 *
 * <p>The caller applies the returned velocity directly every tick, which
 * makes the physics deterministic and overrides vanilla gravity accumulation.
 */
public final class WaterPhysics {

    /** Upward velocity while fully submerged (blocks/tick). */
    private final double riseVelocity;
    /** Downward velocity while not in water (blocks/tick). */
    private final double sinkVelocity;

    public WaterPhysics(double riseVelocity, double sinkVelocity) {
        this.riseVelocity = riseVelocity;
        this.sinkVelocity = sinkVelocity;
    }

    public static WaterPhysics defaults() {
        return new WaterPhysics(0.35, -0.30);
    }

    /**
     * @param feetInWater true if the block at the hull's base is liquid
     * @param aboveInWater true if the block above the hull's base is liquid
     * @return the vertical velocity to apply this tick (0 = hold at surface)
     */
    public double verticalVelocity(boolean feetInWater, boolean aboveInWater) {
        if (feetInWater && aboveInWater) {
            return riseVelocity;
        }
        if (feetInWater) {
            return 0.0;
        }
        return sinkVelocity;
    }

    public double riseVelocity() {
        return riseVelocity;
    }

    public double sinkVelocity() {
        return sinkVelocity;
    }
}
