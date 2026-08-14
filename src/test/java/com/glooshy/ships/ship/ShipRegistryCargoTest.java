package com.glooshy.ships.ship;

import static org.junit.jupiter.api.Assertions.*;

import com.glooshy.ships.hull.HpCalculator;
import com.glooshy.ships.identity.ShipIdentityGenerator;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for cargo storage on {@link ShipRegistry} (RQCA-21/22).
 *
 * <p>Cargo is stored as slot → raw serialized-item map, so tests use plain
 * maps and never touch org.bukkit.ItemStack.
 */
class ShipRegistryCargoTest {

    private Ship newHullAppliedShipWithCargoModule(ShipRegistry registry) {
        Ship ship = registry.createShip();
        registry.applyHull(ship.identity(), null);
        registry.installModule(ship.identity(), ModuleType.CARGO, ModuleSlot.BOW);
        return registry.find(ship.identity()).orElseThrow();
    }

    @Test
    void setCargo_stores_contents() {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid(), new HpCalculator(10.0));
        Ship ship = newHullAppliedShipWithCargoModule(registry);

        Map<String, Object> item = Map.of("type", "STONE", "amount", 3);
        Ship updated = registry.setCargo(ship.identity(), Map.of(4, item));

        assertEquals(Map.of(4, item), updated.cargo());
        assertEquals(Map.of(4, item),
                registry.find(ship.identity()).orElseThrow().cargo());
    }

    @Test
    void setCargo_overwrites_previous_contents_entirely() {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid(), new HpCalculator(10.0));
        Ship ship = newHullAppliedShipWithCargoModule(registry);

        registry.setCargo(ship.identity(), Map.of(0, Map.of("type", "DIRT")));
        Map<Integer, Map<String, Object>> fresh = Map.of(26, Map.of("type", "DIAMOND"));
        Ship updated = registry.setCargo(ship.identity(), fresh);

        assertEquals(fresh, updated.cargo(), "Bulk setter replaces, not merges");
    }

    @Test
    void setCargo_throws_for_unknown_ship() {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid(), new HpCalculator(10.0));
        Ship ship = registry.createShip();
        registry.remove(ship.identity());

        assertThrows(IllegalStateException.class,
                () -> registry.setCargo(ship.identity(), Map.of()));
    }

    @Test
    void cargo_survives_damage() {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid(), new HpCalculator(10.0));
        Ship ship = newHullAppliedShipWithCargoModule(registry);
        Map<Integer, Map<String, Object>> cargo = Map.of(2, Map.of("type", "STONE"));
        registry.setCargo(ship.identity(), cargo);
        registry.transition(ship.identity(), LifecyclePhase.FINALIZED);

        Ship after = registry.applyDamage(ship.identity(), 1.0);

        assertEquals(cargo, after.cargo(), "Cargo must survive damage (RQCA-22)");
    }

    @Test
    void cargo_survives_finalization() {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid(), new HpCalculator(10.0));
        Ship ship = newHullAppliedShipWithCargoModule(registry);
        Map<Integer, Map<String, Object>> cargo = Map.of(10, Map.of("type", "TORCH", "amount", 64));
        registry.setCargo(ship.identity(), cargo);

        Ship finalized = registry.transition(ship.identity(), LifecyclePhase.FINALIZED);

        assertEquals(cargo, finalized.cargo());
    }

    @Test
    void cargo_survives_module_changes() {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid(), new HpCalculator(10.0));
        Ship ship = newHullAppliedShipWithCargoModule(registry);
        Map<Integer, Map<String, Object>> cargo = Map.of(1, Map.of("type", "IRON_INGOT"));
        registry.setCargo(ship.identity(), cargo);

        registry.installModule(ship.identity(), ModuleType.CANNON, ModuleSlot.STERN);
        registry.moveModule(ship.identity(), ModuleSlot.BOW, ModuleSlot.PORT);
        Ship after = registry.removeModule(ship.identity(), ModuleSlot.PORT);

        assertEquals(cargo, after.cargo(), "Cargo must survive module rearrangement");
        assertFalse(after.hasCargoModule(), "Cargo module was removed in the same sequence");
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
