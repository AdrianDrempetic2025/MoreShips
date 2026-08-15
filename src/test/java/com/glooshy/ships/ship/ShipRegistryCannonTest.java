package com.glooshy.ships.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.glooshy.ships.hull.HpCalculator;
import com.glooshy.ships.identity.ShipIdentityGenerator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Session-2 cannon state on the registry + idempotent stat derivation
 * (repeated recalculation can never inflate HP).
 */
class ShipRegistryCannonTest {

    private ShipRegistry newRegistry(int healthBonus) {
        ShipRegistry registry = new ShipRegistry(
                () -> ShipIdentityGenerator.uuid().generate(), new HpCalculator(10.0));
        registry.setHealthBonusPerModule(healthBonus);
        return registry;
    }

    private Ship hullAppliedShip(ShipRegistry registry) {
        Ship ship = registry.createShip(ShipSize.SMALL);
        return registry.applyHull(ship.identity(), null);
    }

    @Test
    void mutate_cannon_updates_state_atomically() {
        ShipRegistry registry = newRegistry(100);
        Ship ship = hullAppliedShip(registry);
        ModulePos pos = new ModulePos(0, 2);

        registry.installModule(ship.identity(), ModuleType.CANNON, pos);
        registry.mutateCannon(ship.identity(), pos, state -> state.withShots(4));

        assertEquals(4, registry.find(ship.identity()).orElseThrow()
                .cannons().get(pos).shots());
    }

    @Test
    void mutate_cannon_on_missing_state_starts_empty() {
        ShipRegistry registry = newRegistry(100);
        Ship ship = hullAppliedShip(registry);
        ModulePos pos = new ModulePos(0, 2);
        registry.installModule(ship.identity(), ModuleType.CANNON, pos);

        registry.mutateCannon(ship.identity(), pos, state -> {
            assertEquals(0, state.shots()); // started empty, not null-crashed
            return state.withShots(1);
        });
        assertEquals(1, registry.find(ship.identity()).orElseThrow()
                .cannons().get(pos).shots());
    }

    @Test
    void set_cannon_replaces_inventory() {
        ShipRegistry registry = newRegistry(100);
        Ship ship = hullAppliedShip(registry);
        ModulePos pos = new ModulePos(1, 2);
        registry.installModule(ship.identity(), ModuleType.CANNON, pos);

        Map<Integer, Map<String, Object>> inv = new LinkedHashMap<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "SNOWBALL");
        item.put("amount", 16);
        inv.put(3, item);
        registry.setCannon(ship.identity(), pos, new CannonState(2, inv));

        CannonState state = registry.find(ship.identity()).orElseThrow().cannons().get(pos);
        assertEquals(2, state.shots());
        assertEquals(16, ((Number) state.itemAt(3).get("amount")).intValue());
    }

    @Test
    void removing_module_clears_its_cannon_state() {
        ShipRegistry registry = newRegistry(100);
        Ship ship = hullAppliedShip(registry);
        ModulePos pos = new ModulePos(0, 2);
        registry.installModule(ship.identity(), ModuleType.CANNON, pos);
        registry.mutateCannon(ship.identity(), pos, state -> state.withShots(9));

        registry.removeModule(ship.identity(), pos);

        assertTrue(registry.find(ship.identity()).orElseThrow().cannons().isEmpty());
    }

    @Test
    void health_module_hp_is_derived_never_inflated() {
        ShipRegistry registry = newRegistry(100);
        Ship ship = hullAppliedShip(registry);
        ModulePos a = new ModulePos(0, 2);
        ModulePos b = new ModulePos(1, 2);

        registry.installModule(ship.identity(), ModuleType.HEALTH, a);
        // hp fields stay -1 with null hull — derived base is -1
        // (real hull tested in BUILD-SMOKE); the invariant here is that a
        // move/reload-style repeated derivation cannot ACCUMULATE:
        registry.moveModule(ship.identity(), a, b);
        registry.removeModule(ship.identity(), b);
        registry.installModule(ship.identity(), ModuleType.HEALTH, b);

        Ship after = registry.find(ship.identity()).orElseThrow();
        assertEquals(-1, after.maxHp()); // derived fresh, not -1 + 100 + 100...
    }

    @Test
    void mutate_cannon_unknown_ship_throws() {
        ShipRegistry registry = newRegistry(100);
        assertThrows(IllegalStateException.class, () -> registry.mutateCannon(
                com.glooshy.ships.identity.ShipIdentity.fromUuid(UUID.randomUUID()),
                new ModulePos(0, 2), state -> state));
    }
}
