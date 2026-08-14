package com.glooshy.ships.ship;

import static org.junit.jupiter.api.Assertions.*;

import com.glooshy.ships.hull.HpCalculator;
import com.glooshy.ships.identity.ShipIdentityGenerator;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for per-module cargo holds on {@link ShipRegistry} (RQCA-21/22).
 *
 * <p>Every CARGO module slot has its OWN hold — two cargo modules never share
 * inventory. Cargo is stored as inventory index → raw serialized-item map, so
 * tests use plain maps and never touch org.bukkit.ItemStack.
 */
class ShipRegistryCargoTest {

    private Ship newHullAppliedShipWithCargoModule(ShipRegistry registry) {
        return newHullAppliedShipWithCargoModule(registry, ModuleSlot.BOW);
    }

    private Ship newHullAppliedShipWithCargoModule(ShipRegistry registry, ModuleSlot slot) {
        Ship ship = registry.createShip();
        registry.applyHull(ship.identity(), null);
        registry.installModule(ship.identity(), ModuleType.CARGO, slot);
        return registry.find(ship.identity()).orElseThrow();
    }

    @Test
    void setCargo_stores_hold_for_one_module_slot() {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid(), new HpCalculator(10.0));
        Ship ship = newHullAppliedShipWithCargoModule(registry);

        Map<String, Object> item = Map.of("type", "STONE", "amount", 3);
        Ship updated = registry.setCargo(ship.identity(), ModuleSlot.BOW, Map.of(4, item));

        assertEquals(Map.of(4, item), updated.cargo().get(ModuleSlot.BOW));
        assertEquals(Map.of(4, item),
                registry.find(ship.identity()).orElseThrow().cargo().get(ModuleSlot.BOW));
    }

    @Test
    void two_cargo_modules_have_separate_holds() {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid(), new HpCalculator(10.0));
        Ship ship = registry.createShip();
        registry.applyHull(ship.identity(), null);
        registry.installModule(ship.identity(), ModuleType.CARGO, ModuleSlot.BOW);
        registry.installModule(ship.identity(), ModuleType.CARGO, ModuleSlot.STERN);

        Map<String, Object> stones = Map.of("type", "STONE");
        Map<String, Object> diamonds = Map.of("type", "DIAMOND");
        registry.setCargo(ship.identity(), ModuleSlot.BOW, Map.of(0, stones));
        registry.setCargo(ship.identity(), ModuleSlot.STERN, Map.of(0, diamonds));

        Ship fromRegistry = registry.find(ship.identity()).orElseThrow();
        assertEquals(Map.of(0, stones), fromRegistry.cargo().get(ModuleSlot.BOW),
                "BOW hold must not leak into STERN");
        assertEquals(Map.of(0, diamonds), fromRegistry.cargo().get(ModuleSlot.STERN),
                "STERN hold must not leak into BOW");
    }

    @Test
    void setCargo_overwrites_previous_hold_entirely() {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid(), new HpCalculator(10.0));
        Ship ship = newHullAppliedShipWithCargoModule(registry);

        registry.setCargo(ship.identity(), ModuleSlot.BOW, Map.of(0, Map.of("type", "DIRT")));
        Map<Integer, Map<String, Object>> fresh = Map.of(26, Map.of("type", "DIAMOND"));
        Ship updated = registry.setCargo(ship.identity(), ModuleSlot.BOW, fresh);

        assertEquals(fresh, updated.cargo().get(ModuleSlot.BOW), "Bulk setter replaces, not merges");
    }

    @Test
    void setCargo_throws_for_unknown_ship() {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid(), new HpCalculator(10.0));
        Ship ship = registry.createShip();
        registry.remove(ship.identity());

        assertThrows(IllegalStateException.class,
                () -> registry.setCargo(ship.identity(), ModuleSlot.BOW, Map.of()));
    }

    @Test
    void moving_a_cargo_module_transfers_its_hold() {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid(), new HpCalculator(10.0));
        Ship ship = newHullAppliedShipWithCargoModule(registry);
        Map<Integer, Map<String, Object>> hold = Map.of(3, Map.of("type", "GOLD_INGOT"));
        registry.setCargo(ship.identity(), ModuleSlot.BOW, hold);

        Ship moved = registry.moveModule(ship.identity(), ModuleSlot.BOW, ModuleSlot.PORT);

        assertNull(moved.cargo().get(ModuleSlot.BOW), "Old slot must not keep the hold");
        assertEquals(hold, moved.cargo().get(ModuleSlot.PORT),
                "The hold travels with the module");
    }

    @Test
    void cargo_survives_damage() {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid(), new HpCalculator(10.0));
        Ship ship = newHullAppliedShipWithCargoModule(registry);
        Map<Integer, Map<String, Object>> cargo = Map.of(2, Map.of("type", "STONE"));
        registry.setCargo(ship.identity(), ModuleSlot.BOW, cargo);
        registry.transition(ship.identity(), LifecyclePhase.FINALIZED);

        Ship after = registry.applyDamage(ship.identity(), 1.0);

        assertEquals(cargo, after.cargo().get(ModuleSlot.BOW), "Cargo must survive damage (RQCA-22)");
    }

    @Test
    void cargo_survives_finalization() {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid(), new HpCalculator(10.0));
        Ship ship = newHullAppliedShipWithCargoModule(registry);
        Map<Integer, Map<String, Object>> cargo = Map.of(10, Map.of("type", "TORCH", "amount", 64));
        registry.setCargo(ship.identity(), ModuleSlot.BOW, cargo);

        Ship finalized = registry.transition(ship.identity(), LifecyclePhase.FINALIZED);

        assertEquals(cargo, finalized.cargo().get(ModuleSlot.BOW));
    }

    @Test
    void cargo_survives_unrelated_module_changes() {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid(), new HpCalculator(10.0));
        Ship ship = newHullAppliedShipWithCargoModule(registry);
        Map<Integer, Map<String, Object>> cargo = Map.of(1, Map.of("type", "IRON_INGOT"));
        registry.setCargo(ship.identity(), ModuleSlot.BOW, cargo);

        registry.installModule(ship.identity(), ModuleType.CANNON, ModuleSlot.STERN);
        registry.moveModule(ship.identity(), ModuleSlot.STERN, ModuleSlot.PORT);
        Ship after = registry.removeModule(ship.identity(), ModuleSlot.PORT);

        assertEquals(cargo, after.cargo().get(ModuleSlot.BOW),
                "Cargo must survive unrelated module rearrangement");
        assertTrue(after.hasCargoModule());
    }

    @Test
    void hasCargoModule_reflects_fitted_modules() {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid(), new HpCalculator(10.0));
        Ship ship = registry.createShip();
        registry.applyHull(ship.identity(), null);
        assertFalse(registry.find(ship.identity()).orElseThrow().hasCargoModule());

        registry.installModule(ship.identity(), ModuleType.CARGO, ModuleSlot.STERN);
        assertTrue(registry.find(ship.identity()).orElseThrow().hasCargoModule());

        registry.removeModule(ship.identity(), ModuleSlot.STERN);
        assertFalse(registry.find(ship.identity()).orElseThrow().hasCargoModule());
    }
}
