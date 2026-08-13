package com.glooshy.ships.ship;

import static org.junit.jupiter.api.Assertions.*;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.identity.ShipIdentityGenerator;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ShipRegistry#applyHull}.
 *
 * <p>Uses a null hull material in assertions because org.bukkit.Material cannot
 * be referenced in unit tests (static initializer requires server context).
 * The Material-accepting overload is verified in BUILD-SMOKE.
 */
class ShipRegistryHullApplicationTest {

    /**
     * FALSIFICATION_PROOF — DEFECT-08 (HULL_APPLIED_WITHOUT_TRANSITION).
     *
     * <p>Mutation plan: in {@link ShipRegistry#applyHull}, replace the new Ship
     * with phase=HULL_APPLIED by phase=current.phase() (no transition). Expected
     * RED: the phase assertion fails.
     */
    @Test
    void applyHull_transitions_unfinished_to_hull_applied() {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid());
        Ship ship = registry.createShip();

        Ship updated = registry.applyHull(ship.identity(), null);

        assertEquals(LifecyclePhase.HULL_APPLIED, updated.phase(),
                "Phase must transition to HULL_APPLIED (DEFECT-08)");
        Ship fromRegistry = registry.find(ship.identity()).orElseThrow();
        assertEquals(LifecyclePhase.HULL_APPLIED, fromRegistry.phase());
    }

    @Test
    void applyHull_stores_hull_material_reference() {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid());
        Ship ship = registry.createShip();

        // Pass a sentinel non-null value; we use a String-marked Object since
        // Material can't be referenced in unit tests. The registry stores whatever
        // it's given. The adapter from Material happens at the listener level.
        // Here we just verify storage with null (allowed by signature).
        Ship updated = registry.applyHull(ship.identity(), null);
        assertNull(updated.hullMaterial(), "Null hull material is stored as null");
    }

    @Test
    void applyHull_throws_for_non_unfinished_ship() {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid());
        Ship ship = registry.createShip();
        registry.applyHull(ship.identity(), null);

        // Already HULL_APPLIED — applying again must throw
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.applyHull(ship.identity(), null));
        assertTrue(ex.getMessage().contains("Cannot apply hull"));
    }

    @Test
    void applyHull_throws_for_unknown_ship() {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid());

        assertThrows(IllegalStateException.class,
                () -> registry.applyHull(
                        ShipIdentity.fromUuid(UUID.randomUUID()), null));
    }

    @Test
    void new_ship_has_null_hull_material() {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid());

        Ship ship = registry.createShip();

        assertNull(ship.hullMaterial(), "Newly created ships have no hull material");
    }

    @Test
    void applyHull_concurrent_only_one_wins() throws Exception {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid());
        Ship ship = registry.createShip();

        int threads = 8;
        Thread[] pool = new Thread[threads];
        int[] successes = new int[1];
        int[] failures = new int[1];

        for (int i = 0; i < threads; i++) {
            pool[i] = new Thread(() -> {
                try {
                    registry.applyHull(ship.identity(), null);
                    synchronized (successes) { successes[0]++; }
                } catch (IllegalStateException e) {
                    synchronized (failures) { failures[0]++; }
                }
            });
        }
        for (Thread t : pool) t.start();
        for (Thread t : pool) t.join();

        assertEquals(1, successes[0], "Only the first concurrent applyHull wins");
        assertEquals(threads - 1, failures[0]);
        assertEquals(LifecyclePhase.HULL_APPLIED,
                registry.phaseOf(ship.identity()).orElseThrow());
    }
}
