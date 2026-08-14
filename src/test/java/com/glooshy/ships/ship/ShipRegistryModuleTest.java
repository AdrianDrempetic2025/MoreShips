package com.glooshy.ships.ship;

import static org.junit.jupiter.api.Assertions.*;

import com.glooshy.ships.hull.HpCalculator;
import com.glooshy.ships.identity.ShipIdentityGenerator;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for module install/remove/move on {@link ShipRegistry} (RQCA-08).
 *
 * <p>Phase rule: module changes are only valid on HULL_APPLIED ships — after
 * finalization the arrangement is frozen until destruction.
 */
class ShipRegistryModuleTest {

    private ShipRegistry newRegistry() {
        return new ShipRegistry(ShipIdentityGenerator.uuid(), new HpCalculator(10.0));
    }

    private Ship newHullAppliedShip(ShipRegistry registry) {
        Ship ship = registry.createShip(com.glooshy.ships.ship.ShipSize.MEDIUM);
        return registry.applyHull(ship.identity(), null);
    }

    @Test
    void installModule_fits_module_into_free_slot() {
        ShipRegistry registry = newRegistry();
        Ship ship = newHullAppliedShip(registry);

        Ship updated = registry.installModule(ship.identity(), ModuleType.CARGO, new ModulePos(1, 0));

        assertEquals(Map.of(new ModulePos(1, 0), ModuleType.CARGO), updated.modules());
        assertEquals(Map.of(new ModulePos(1, 0), ModuleType.CARGO),
                registry.find(ship.identity()).orElseThrow().modules());
    }

    @Test
    void installModule_fills_multiple_slots_independently() {
        ShipRegistry registry = newRegistry();
        Ship ship = newHullAppliedShip(registry);

        registry.installModule(ship.identity(), ModuleType.CANNON, new ModulePos(1, 0));
        registry.installModule(ship.identity(), ModuleType.CANNON, new ModulePos(1, 3));
        registry.installModule(ship.identity(), ModuleType.SEAT, new ModulePos(0, 0));
        registry.installModule(ship.identity(), ModuleType.CANNON, new ModulePos(2, 0));

        Ship fromRegistry = registry.find(ship.identity()).orElseThrow();
        assertEquals(4, fromRegistry.modules().size());
        assertEquals(ModuleType.CANNON, fromRegistry.modules().get(new ModulePos(2, 0)));
    }

    @Test
    void installModule_refuses_occupied_slot() {
        ShipRegistry registry = newRegistry();
        Ship ship = newHullAppliedShip(registry);
        registry.installModule(ship.identity(), ModuleType.CARGO, new ModulePos(1, 0));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.installModule(ship.identity(), ModuleType.CANNON, new ModulePos(1, 0)));
        assertTrue(ex.getMessage().contains("occupied"));

        // Original arrangement unchanged
        assertEquals(ModuleType.CARGO,
                registry.find(ship.identity()).orElseThrow().modules().get(new ModulePos(1, 0)));
    }

    @Test
    void installModule_refuses_unfinished_ship() {
        ShipRegistry registry = newRegistry();
        Ship ship = registry.createShip(com.glooshy.ships.ship.ShipSize.MEDIUM);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.installModule(ship.identity(), ModuleType.CARGO, new ModulePos(1, 0)));
        assertTrue(ex.getMessage().contains("HULL_APPLIED"));
    }

    @Test
    void installModule_refuses_finalized_ship() {
        ShipRegistry registry = newRegistry();
        Ship ship = newHullAppliedShip(registry);
        registry.transition(ship.identity(), LifecyclePhase.FINALIZED);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.installModule(ship.identity(), ModuleType.CARGO, new ModulePos(1, 0)));
        assertTrue(ex.getMessage().contains("FINALIZED"));
    }

    @Test
    void removeModule_clears_slot() {
        ShipRegistry registry = newRegistry();
        Ship ship = newHullAppliedShip(registry);
        registry.installModule(ship.identity(), ModuleType.SEAT, new ModulePos(0, 0));

        Ship updated = registry.removeModule(ship.identity(), new ModulePos(0, 0));

        assertTrue(updated.modules().isEmpty());
        assertTrue(registry.find(ship.identity()).orElseThrow().modules().isEmpty());
    }

    @Test
    void removeModule_refuses_empty_slot() {
        ShipRegistry registry = newRegistry();
        Ship ship = newHullAppliedShip(registry);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.removeModule(ship.identity(), new ModulePos(1, 0)));
        assertTrue(ex.getMessage().contains("empty"));
    }

    @Test
    void removeModule_refuses_finalized_ship() {
        ShipRegistry registry = newRegistry();
        Ship ship = newHullAppliedShip(registry);
        registry.installModule(ship.identity(), ModuleType.CARGO, new ModulePos(1, 0));
        registry.transition(ship.identity(), LifecyclePhase.FINALIZED);

        assertThrows(IllegalStateException.class,
                () -> registry.removeModule(ship.identity(), new ModulePos(1, 0)));
        // Module survives — frozen after finalization
        assertEquals(ModuleType.CARGO,
                registry.find(ship.identity()).orElseThrow().modules().get(new ModulePos(1, 0)));
    }

    @Test
    void moveModule_relocates_module_to_free_slot() {
        ShipRegistry registry = newRegistry();
        Ship ship = newHullAppliedShip(registry);
        registry.installModule(ship.identity(), ModuleType.CANNON, new ModulePos(1, 0));

        Ship updated = registry.moveModule(ship.identity(), new ModulePos(1, 0), new ModulePos(1, 3));

        assertFalse(updated.modules().containsKey(new ModulePos(1, 0)));
        assertEquals(ModuleType.CANNON, updated.modules().get(new ModulePos(1, 3)));
        assertEquals(1, updated.modules().size());
    }

    @Test
    void moveModule_refuses_occupied_target() {
        ShipRegistry registry = newRegistry();
        Ship ship = newHullAppliedShip(registry);
        registry.installModule(ship.identity(), ModuleType.CANNON, new ModulePos(1, 0));
        registry.installModule(ship.identity(), ModuleType.CARGO, new ModulePos(1, 3));

        assertThrows(IllegalStateException.class,
                () -> registry.moveModule(ship.identity(), new ModulePos(1, 0), new ModulePos(1, 3)));
    }

    @Test
    void moveModule_refuses_empty_source() {
        ShipRegistry registry = newRegistry();
        Ship ship = newHullAppliedShip(registry);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.moveModule(ship.identity(), new ModulePos(1, 0), new ModulePos(0, 0)));
        assertTrue(ex.getMessage().contains("empty"));
    }

    @Test
    void finalization_preserves_installed_modules() {
        ShipRegistry registry = newRegistry();
        Ship ship = newHullAppliedShip(registry);
        registry.installModule(ship.identity(), ModuleType.CARGO, new ModulePos(1, 0));
        registry.installModule(ship.identity(), ModuleType.SEAT, new ModulePos(2, 0));

        Ship finalized = registry.transition(ship.identity(), LifecyclePhase.FINALIZED);

        assertEquals(2, finalized.modules().size());
        assertEquals(ModuleType.SEAT, finalized.modules().get(new ModulePos(2, 0)));
    }

    @Test
    void damage_preserves_installed_modules() {
        ShipRegistry registry = newRegistry();
        Ship ship = newHullAppliedShip(registry);
        registry.installModule(ship.identity(), ModuleType.CARGO, new ModulePos(1, 0));
        registry.transition(ship.identity(), LifecyclePhase.FINALIZED);

        Ship after = registry.applyDamage(ship.identity(), 1.0);

        // Note: persisted-HP restore is a known gap (HP is -1 after restart);
        // applyDamage clamps to 0. Modules must survive regardless.
        assertEquals(Map.of(new ModulePos(1, 0), ModuleType.CARGO), after.modules());
    }
}
