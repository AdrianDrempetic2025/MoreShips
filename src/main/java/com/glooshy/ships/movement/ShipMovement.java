package com.glooshy.ships.movement;

/**
 * Pure-logic velocity state for a single ship.
 *
 * <p>Holds the ship's current forward speed and steps it toward a target speed
 * (maxSpeed when engaged, 0 when disengaged) at a per-tick rate. Acceleration
 * applies while speeding up; friction applies while slowing down. Both are
 * clamped at the target so the value never overshoots.
 *
 * <p>This is the FALSIFICATION_PROOF surface for DEFECT-14 (movement does not
 * actually accelerate — ship jumps to max speed instantly). The mutation that
 * must RED is "tick() returns without modifying currentSpeed" or
 * "engage() sets currentSpeed = maxSpeed directly".
 *
 * <p>Bukkit-free: no dependency on entity API, fully unit-testable.
 */
public final class ShipMovement {

    private final double maxSpeed;
    private final double acceleration;
    private final double friction;

    private double currentSpeed;
    private boolean engaged;

    public ShipMovement(double maxSpeed, double acceleration, double friction) {
        if (maxSpeed < 0.0) {
            throw new IllegalArgumentException(
                    "maxSpeed must be non-negative, got " + maxSpeed);
        }
        if (acceleration <= 0.0) {
            throw new IllegalArgumentException(
                    "acceleration must be positive, got " + acceleration);
        }
        if (friction <= 0.0) {
            throw new IllegalArgumentException(
                    "friction must be positive, got " + friction);
        }
        this.maxSpeed = maxSpeed;
        this.acceleration = acceleration;
        this.friction = friction;
    }

    /**
     * Mark this ship as piloted. Subsequent {@link #tick()} calls accelerate
     * toward {@link #maxSpeed()}.
     */
    public void engage() {
        engaged = true;
    }

    /**
     * Mark this ship as un-piloted. Subsequent {@link #tick()} calls decelerate
     * toward 0 using friction.
     */
    /** Hard brake: S key — shed speed much faster than water friction alone. */
    public void brake() {
        currentSpeed = Math.max(0.0, currentSpeed - friction * 4.0);
        engaged = false;
    }

    public void disengage() {
        engaged = false;
    }

    /**
     * Advance one tick. Move currentSpeed toward the current target (maxSpeed
     * if engaged, 0 if not) by acceleration or friction respectively.
     */
    public void tick() {
        double target = engaged ? maxSpeed : 0.0;
        if (currentSpeed < target) {
            currentSpeed = Math.min(target, currentSpeed + acceleration);
        } else if (currentSpeed > target) {
            currentSpeed = Math.max(target, currentSpeed - friction);
        }
    }

    public double currentSpeed() {
        return currentSpeed;
    }

    public boolean isEngaged() {
        return engaged;
    }

    /**
     * True when the ship has non-negligible forward motion. Use this to skip
     * velocity application on stationary ships.
     */
    public boolean isMoving() {
        return currentSpeed > 1e-9;
    }

    public double maxSpeed() {
        return maxSpeed;
    }

    public double acceleration() {
        return acceleration;
    }

    public double friction() {
        return friction;
    }
}
