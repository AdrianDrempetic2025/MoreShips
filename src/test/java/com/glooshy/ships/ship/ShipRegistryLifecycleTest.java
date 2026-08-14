package com.glooshy.ships.ship;

import static org.junit.jupiter.api.Assertions.*;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.identity.ShipIdentityGenerator;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Lifecycle tests on {@link ShipRegistry}: initial phase is UNFINISHED,
 * valid transitions succeed, invalid transitions throw.
 */
class ShipRegistryLifecycleTest {

    /**
     * FALSIFICATION_PROOF — DEFECT-06: INVALID_INITIAL_PHASE.
     *
     * <p>Named defect: a newly-created ship does not start in UNFINISHED. This
     * breaks the SCIN-01 lifecycle (placed = UNFINISHED).
     *
     * <p>Mutation plan: change {@code new Ship(identity, LifecyclePhase.UNFINISHED)}
     * to a different phase (e.g., FINALIZED) in {@link ShipRegistry#createShip()}.
     * Expected RED: the assertion that phase == UNFINISHED fails.
     */
    @Test
    void createShip_starts_in_unfinished_phase() {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid(), new com.glooshy.ships.hull.HpCalculator(10.0));

        Ship ship = registry.createShip(com.glooshy.ships.ship.ShipSize.MEDIUM);

        assertEquals(LifecyclePhase.UNFINISHED, ship.phase(),
                "New ships must start UNFINISHED (DEFECT-06)");
    }

    @Test
    void transition_applies_valid_phase_change() {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid(), new com.glooshy.ships.hull.HpCalculator(10.0));
        Ship ship = registry.createShip(com.glooshy.ships.ship.ShipSize.MEDIUM);

        Ship transitioned = registry.transition(ship.identity(), LifecyclePhase.HULL_APPLIED);

        assertEquals(LifecyclePhase.HULL_APPLIED, transitioned.phase());
        assertEquals(LifecyclePhase.HULL_APPLIED, registry.phaseOf(ship.identity()).orElseThrow());
    }

    @Test
    void transition_throws_on_invalid_path() {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid(), new com.glooshy.ships.hull.HpCalculator(10.0));
        Ship ship = registry.createShip(com.glooshy.ships.ship.ShipSize.MEDIUM);

        // UNFINISHED → FINALIZED is invalid (must go through HULL_APPLIED)
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.transition(ship.identity(), LifecyclePhase.FINALIZED));
        assertTrue(ex.getMessage().contains("Invalid lifecycle transition"),
                "Exception must mention invalid transition");
    }

    @Test
    void transition_throws_on_unknown_ship() {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid(), new com.glooshy.ships.hull.HpCalculator(10.0));
        ShipIdentity unknown = ShipIdentity.fromUuid(UUID.randomUUID());

        assertThrows(IllegalStateException.class,
                () -> registry.transition(unknown, LifecyclePhase.HULL_APPLIED));
    }

    @Test
    void transition_is_atomic_under_concurrent_attempts() throws Exception {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid(), new com.glooshy.ships.hull.HpCalculator(10.0));
        Ship ship = registry.createShip(com.glooshy.ships.ship.ShipSize.MEDIUM);

        int threads = 8;
        Thread[] pool = new Thread[threads];
        int[] successes = new int[1];
        int[] failures = new int[1];

        for (int i = 0; i < threads; i++) {
            pool[i] = new Thread(() -> {
                try {
                    registry.transition(ship.identity(), LifecyclePhase.HULL_APPLIED);
                    synchronized (successes) {
                        successes[0]++;
                    }
                } catch (IllegalStateException e) {
                    synchronized (failures) {
                        failures[0]++;
                    }
                }
            });
        }
        for (Thread t : pool) t.start();
        for (Thread t : pool) t.join();

        assertEquals(1, successes[0],
                "Exactly one transition must succeed; the rest must fail because the ship is no longer UNFINISHED");
        assertEquals(threads - 1, failures[0]);
        assertEquals(LifecyclePhase.HULL_APPLIED, registry.phaseOf(ship.identity()).orElseThrow());
    }
}
