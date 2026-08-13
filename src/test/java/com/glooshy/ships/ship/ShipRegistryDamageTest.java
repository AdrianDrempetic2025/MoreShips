package com.glooshy.ships.ship;

import static org.junit.jupiter.api.Assertions.*;

import com.glooshy.ships.hull.HpCalculator;
import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.identity.ShipIdentityGenerator;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ShipRegistry#applyDamage} — HP reduction on FINALIZED ships.
 */
class ShipRegistryDamageTest {

    private ShipRegistry newRegistry() {
        return new ShipRegistry(ShipIdentityGenerator.uuid(), new HpCalculator(10.0));
    }

    @Test
    void applyDamage_reduces_hp_on_finalized_ship() {
        ShipRegistry registry = newRegistry();
        Ship ship = registry.createShip();
        registry.applyHull(ship.identity(), null); // hull applied (HP set to -1 because hardness unknown from null)
        registry.transition(ship.identity(), LifecyclePhase.FINALIZED);

        // With null hull material, hp is -1. Set up properly using direct HP.
        // For this test, we test applyDamage logic directly by setting up a ship
        // with explicit HP via the 5-arg constructor.
        // (applyHull with null material yields maxHp=-1; in production the
        // listener passes a real Material so hardness is known.)

        // Replace with a ship that has explicit HP via the load path:
        registry.remove(ship.identity());
        Ship explicit = new Ship(ship.identity(), LifecyclePhase.FINALIZED, null, 50, 50);
        registry.load(java.util.List.of(explicit));

        Ship after = registry.applyDamage(ship.identity(), 10);

        assertEquals(40, after.currentHp(), "10 damage reduces 50 HP to 40");
        assertEquals(50, after.maxHp(), "Max HP unchanged");
    }

    @Test
    void applyDamage_clamps_to_zero() {
        ShipRegistry registry = newRegistry();
        ShipIdentity id = ShipIdentityGenerator.uuid().generate();
        Ship ship = new Ship(id, LifecyclePhase.FINALIZED, null, 5, 5);
        registry.load(java.util.List.of(ship));

        Ship after = registry.applyDamage(id, 100);

        assertEquals(0, after.currentHp(), "Damage beyond current HP clamps to 0");
    }

    @Test
    void applyDamage_throws_for_non_finalized_ship() {
        ShipRegistry registry = newRegistry();
        Ship ship = registry.createShip(); // UNFINISHED

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.applyDamage(ship.identity(), 5));
        assertTrue(ex.getMessage().contains("FINALIZED"),
                "Error must mention FINALIZED");
    }

    @Test
    void applyDamage_throws_for_unknown_ship() {
        ShipRegistry registry = newRegistry();

        assertThrows(IllegalStateException.class,
                () -> registry.applyDamage(
                        com.glooshy.ships.identity.ShipIdentity.fromUuid(java.util.UUID.randomUUID()),
                        5));
    }

    @Test
    void applyDamage_rejects_negative_amount() {
        ShipRegistry registry = newRegistry();
        ShipIdentity id = ShipIdentityGenerator.uuid().generate();
        Ship ship = new Ship(id, LifecyclePhase.FINALIZED, null, 50, 50);
        registry.load(java.util.List.of(ship));

        assertThrows(IllegalArgumentException.class,
                () -> registry.applyDamage(id, -5));
    }

    /**
     * FALSIFICATION_PROOF — DEFECT-13 (DAMAGE_DOES_NOT_REDUCE_HP).
     *
     * <p>Mutation plan: replace {@code applyDamage} body with
     * {@code return current;} (no HP change). Expected RED: HP assertion fails.
     */
    @Test
    void applyDamage_actually_changes_hp() {
        ShipRegistry registry = newRegistry();
        ShipIdentity id = ShipIdentityGenerator.uuid().generate();
        Ship ship = new Ship(id, LifecyclePhase.FINALIZED, null, 100, 100);
        registry.load(java.util.List.of(ship));

        Ship after = registry.applyDamage(id, 30);

        assertNotEquals(100, after.currentHp(),
                "HP must change after damage (DEFECT-13)");
        assertEquals(70, after.currentHp());
    }

    @Test
    void applyDamage_atomic_under_concurrency() throws Exception {
        ShipRegistry registry = newRegistry();
        ShipIdentity id = ShipIdentityGenerator.uuid().generate();
        Ship ship = new Ship(id, LifecyclePhase.FINALIZED, null, 1000, 1000);
        registry.load(java.util.List.of(ship));

        int threads = 10;
        Thread[] pool = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            pool[i] = new Thread(() -> registry.applyDamage(id, 10));
        }
        for (Thread t : pool) t.start();
        for (Thread t : pool) t.join();

        Ship after = registry.find(id).orElseThrow();
        assertEquals(900, after.currentHp(),
                "10 threads × 10 damage = 100 total; ship went from 1000 to 900");
    }
}
