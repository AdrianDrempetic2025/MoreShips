package com.glooshy.ships.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.ship.ModulePos;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Session-2 pure cannon math: the 180° firing arc clamp and aim expiry.
 */
class CannonMathTest {

    @Test
    void clamp_keeps_camera_inside_arc() {
        // Camera 30° off a cannon resting at yaw 0 → unchanged
        assertEquals(30.0f, CannonAimTracker.clampToArc(30f, 0f, 90f), 1e-4f);
        assertEquals(-30.0f, CannonAimTracker.clampToArc(-30f, 0f, 90f), 1e-4f);
    }

    @Test
    void clamp_caps_camera_outside_arc() {
        // 150° off → clamped to exactly +90° (arc edge)
        assertEquals(90.0f, CannonAimTracker.clampToArc(150f, 0f, 90f), 1e-4f);
        // Opposite direction (180° off — shooting backwards) → also the edge
        // (wrapDegrees maps +180 and -180 onto the same side)
        assertEquals(90.0f, CannonAimTracker.clampToArc(180f, 0f, 90f), 1e-4f);
        assertEquals(90.0f, CannonAimTracker.clampToArc(-180f, 0f, 90f), 1e-4f);
    }

    @Test
    void clamp_wraps_across_the_180_seam() {
        // Resting 170°, camera -170°: only 20° apart across the seam → stays
        float result = CannonAimTracker.clampToArc(-170f, 170f, 90f);
        assertEquals(-170.0f, result, 1e-3f);
        // Resting 170°, camera -60°: 230° apart the long way = 130° the short
        // way → clamped to 90 off 170 → -100 wrapped
        assertEquals(-100.0f, CannonAimTracker.clampToArc(-60f, 170f, 90f), 1e-3f);
    }

    @Test
    void wrap_degrees_normalizes() {
        assertEquals(10f, CannonAimTracker.wrapDegrees(370f), 1e-4f);
        assertEquals(-170f, CannonAimTracker.wrapDegrees(190f), 1e-4f);
        assertEquals(180f, CannonAimTracker.wrapDegrees(180f), 1e-4f);
    }

    @Test
    void aim_expires_after_deadline() throws InterruptedException {
        CannonAimTracker tracker = new CannonAimTracker();
        ShipIdentity id = ShipIdentity.fromUuid(UUID.randomUUID());
        ModulePos pos = new ModulePos(0, 2);
        tracker.set(id, pos, 45f, -10f, System.currentTimeMillis() + 20);
        assertEquals(45f, tracker.live(id, pos).yaw(), 1e-4f);
        Thread.sleep(40);
        assertNull(tracker.live(id, pos)); // resting direction applies again
    }

    @Test
    void clear_drops_all_aims_of_a_ship() {
        CannonAimTracker tracker = new CannonAimTracker();
        ShipIdentity id = ShipIdentity.fromUuid(UUID.randomUUID());
        long until = System.currentTimeMillis() + 60_000;
        tracker.set(id, new ModulePos(0, 2), 10f, 0f, until);
        tracker.set(id, new ModulePos(1, 2), -10f, 0f, until);
        tracker.clear(id);
        assertNull(tracker.live(id, new ModulePos(0, 2)));
        assertNull(tracker.live(id, new ModulePos(1, 2)));
        assertTrue(tracker.live(id, new ModulePos(0, 2)) == null);
    }
}
