package com.glooshy.ships.ship;

import static org.junit.jupiter.api.Assertions.*;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.identity.ShipIdentityGenerator;
import com.glooshy.ships.runtime.RuntimeBinding;
import com.glooshy.ships.runtime.RuntimeBindingRegistry;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * FALSIFICATION_PROOF + REGRESSION_GUARDs for {@link ShipTeardownService}.
 *
 * <p>Named defects:
 * <ul>
 *   <li>DEFECT-04 (TEARDOWN_LOSS) — a pre-finalization input is lost on breakage.
 *       Service-level proxy: teardown does not transition the ship to REMOVED,
 *       leaving it in the live set as an orphan.</li>
 *   <li>DEFECT-04b (BINDING_LEAK) — teardown transitions the ship but forgets
 *       to release the binding.</li>
 *   <li>DEFECT-04c (TEARDOWN_WRONG_PHASE) — teardown transitions a ship that
 *       should not be teardownable (FINALIZED, DESTROYED, REMOVED).</li>
 * </ul>
 *
 * <p>The listener-level "core drop" behavior is verified in BUILD-SMOKE (real
 * server) because it requires a Bukkit world instance.
 */
class ShipTeardownServiceTest {

    @Test
    void isTeardownable_true_for_unfinished_and_hull_applied() {
        assertTrue(ShipTeardownService.isTeardownable(LifecyclePhase.UNFINISHED));
        assertTrue(ShipTeardownService.isTeardownable(LifecyclePhase.HULL_APPLIED));
    }

    @Test
    void isTeardownable_false_for_post_finalization_phases() {
        assertFalse(ShipTeardownService.isTeardownable(LifecyclePhase.FINALIZED));
        assertFalse(ShipTeardownService.isTeardownable(LifecyclePhase.DESTROYED));
        assertFalse(ShipTeardownService.isTeardownable(LifecyclePhase.REMOVED));
    }

    /**
     * FALSIFICATION_PROOF — DEFECT-04 (TEARDOWN_LOSS).
     *
     * <p>Mutation plan: make {@code ShipTeardownService.teardown} a no-op (don't
     * call {@code shipRegistry.transition}). Expected RED: the assertion that
     * phase == REMOVED after teardown fails.
     */
    @Test
    void teardown_transitions_unfinished_ship_to_removed() {
        ShipRegistry ships = new ShipRegistry(ShipIdentityGenerator.uuid());
        RuntimeBindingRegistry bindings = new RuntimeBindingRegistry();
        ShipTeardownService service = new ShipTeardownService(ships, bindings);

        Ship ship = ships.createShip();
        UUID entityUuid = UUID.randomUUID();
        bindings.bind(RuntimeBinding.active(ship.identity(), entityUuid));

        service.teardown(ship.identity());

        Optional<Ship> after = ships.find(ship.identity());
        assertTrue(after.isPresent(), "Ship must still be in the registry (as REMOVED, not deleted)");
        assertEquals(LifecyclePhase.REMOVED, after.get().phase(),
                "Ship must be in REMOVED phase after teardown (DEFECT-04)");
    }

    /**
     * FALSIFICATION_PROOF — DEFECT-04b (BINDING_LEAK).
     *
     * <p>Mutation plan: remove the {@code bindingRegistry.release} call from
     * teardown. Expected RED: the assertion that the binding is empty after
     * teardown fails.
     */
    @Test
    void teardown_releases_binding() {
        ShipRegistry ships = new ShipRegistry(ShipIdentityGenerator.uuid());
        RuntimeBindingRegistry bindings = new RuntimeBindingRegistry();
        ShipTeardownService service = new ShipTeardownService(ships, bindings);

        Ship ship = ships.createShip();
        UUID entityUuid = UUID.randomUUID();
        bindings.bind(RuntimeBinding.active(ship.identity(), entityUuid));

        service.teardown(ship.identity());

        assertTrue(bindings.findByShip(ship.identity()).isEmpty(),
                "Binding must be released after teardown (DEFECT-04b)");
        assertTrue(bindings.findByEntity(entityUuid).isEmpty(),
                "Entity-side index must also be cleared");
        assertEquals(0, bindings.activeCount(),
                "Active count must drop to zero");
    }

    @Test
    void teardown_works_for_hull_applied_phase() {
        ShipRegistry ships = new ShipRegistry(ShipIdentityGenerator.uuid());
        RuntimeBindingRegistry bindings = new RuntimeBindingRegistry();
        ShipTeardownService service = new ShipTeardownService(ships, bindings);

        Ship ship = ships.createShip();
        ships.transition(ship.identity(), LifecyclePhase.HULL_APPLIED);
        bindings.bind(RuntimeBinding.active(ship.identity(), UUID.randomUUID()));

        service.teardown(ship.identity());

        assertEquals(LifecyclePhase.REMOVED,
                ships.phaseOf(ship.identity()).orElseThrow());
    }

    /**
     * FALSIFICATION_PROOF — DEFECT-04c (TEARDOWN_WRONG_PHASE).
     *
     * <p>Mutation plan: remove the phase check in teardown (delete the
     * {@code if (!isTeardownable(ship.phase())) throw ...}). Expected RED: the
     * assertion that teardown throws for FINALIZED fails.
     */
    @Test
    void teardown_throws_for_finalized_ship() {
        ShipRegistry ships = new ShipRegistry(ShipIdentityGenerator.uuid());
        RuntimeBindingRegistry bindings = new RuntimeBindingRegistry();
        ShipTeardownService service = new ShipTeardownService(ships, bindings);

        Ship ship = ships.createShip();
        ships.transition(ship.identity(), LifecyclePhase.HULL_APPLIED);
        ships.transition(ship.identity(), LifecyclePhase.FINALIZED);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.teardown(ship.identity()));
        assertTrue(ex.getMessage().contains("Cannot teardown"),
                "Exception must explain why teardown was refused");
    }

    @Test
    void teardown_throws_for_destroyed_ship() {
        ShipRegistry ships = new ShipRegistry(ShipIdentityGenerator.uuid());
        RuntimeBindingRegistry bindings = new RuntimeBindingRegistry();
        ShipTeardownService service = new ShipTeardownService(ships, bindings);

        ShipIdentity id = ships.createShip().identity();
        ships.transition(id, LifecyclePhase.HULL_APPLIED);
        ships.transition(id, LifecyclePhase.FINALIZED);
        ships.transition(id, LifecyclePhase.DESTROYED);

        assertThrows(IllegalStateException.class, () -> service.teardown(id));
    }

    @Test
    void teardown_throws_for_unknown_ship() {
        ShipRegistry ships = new ShipRegistry(ShipIdentityGenerator.uuid());
        RuntimeBindingRegistry bindings = new RuntimeBindingRegistry();
        ShipTeardownService service = new ShipTeardownService(ships, bindings);

        ShipIdentity unknown = ShipIdentity.fromUuid(UUID.randomUUID());

        assertThrows(IllegalStateException.class, () -> service.teardown(unknown));
    }
}
