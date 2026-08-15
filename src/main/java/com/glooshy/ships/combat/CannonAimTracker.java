package com.glooshy.ships.combat;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.ship.ModulePos;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live aim state per cannon (Session 2): where the barrel currently points
 * and when that aim expires. ONE calculation feeds BOTH the visual barrel
 * rotation and the projectile direction — consumers ask this tracker.
 *
 * <p>When an aim expires (cannon unused past its cooldown + grace), the
 * barrel returns to its resting direction: outward from the module position,
 * aligned with the ship.
 */
public final class CannonAimTracker {

    /** One aimed shot: yaw/pitch the barrel holds until expiry. */
    public record Aim(float yaw, float pitch, long expiresAt) {
    }

    private final Map<String, Aim> aims = new ConcurrentHashMap<>();

    private static String key(ShipIdentity shipId, ModulePos pos) {
        return shipId.encoded() + "|" + pos.encoded();
    }

    public void set(ShipIdentity shipId, ModulePos pos, float yaw, float pitch, long expiresAt) {
        aims.put(key(shipId, pos), new Aim(yaw, pitch, expiresAt));
    }

    /** The live aim, or null when expired/absent (resting direction applies). */
    public Aim live(ShipIdentity shipId, ModulePos pos) {
        Aim aim = aims.get(key(shipId, pos));
        if (aim == null || aim.expiresAt() <= System.currentTimeMillis()) {
            return null;
        }
        return aim;
    }

    public void clear(ShipIdentity shipId) {
        String prefix = shipId.encoded() + "|";
        aims.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /** Wrap an angle to (-180, 180]. */
    public static float wrapDegrees(float angle) {
        float wrapped = angle % 360.0f;
        if (wrapped > 180.0f) {
            wrapped -= 360.0f;
        } else if (wrapped <= -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }

    /**
     * Clamp the camera yaw into the cannon's firing arc: at most
     * {@code halfArcDeg} off the cannon's resting (outward) direction.
     */
    public static float clampToArc(float cameraYaw, float outwardYaw, float halfArcDeg) {
        float diff = wrapDegrees(cameraYaw - outwardYaw);
        float clamped = Math.max(-halfArcDeg, Math.min(halfArcDeg, diff));
        return wrapDegrees(outwardYaw + clamped);
    }
}
