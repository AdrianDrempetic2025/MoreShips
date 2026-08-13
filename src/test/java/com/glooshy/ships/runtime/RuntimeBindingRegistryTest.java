package com.glooshy.ships.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.glooshy.ships.identity.ShipIdentity;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * FALSIFICATION_PROOFs and REGRESSION_GUARDs for {@link RuntimeBindingRegistry}.
 *
 * <p>Two named defects:
 * <ul>
 *   <li>DEFECT-02 DOUBLE_BIND — same entity bound to two ships, or vice versa</li>
 *   <li>DEFECT-03 VANILLA_MISCLASSIFICATION — a non-bound UUID resolves to a binding</li>
 * </ul>
 */
class RuntimeBindingRegistryTest {

    /**
     * FALSIFICATION_PROOF — DEFECT-02 (entity side).
     *
     * <p>Mutation plan: remove the entity-side collision check in {@code bind()}
     * (the {@code byEntity.putIfAbsent} block + rollback). Re-run — expected RED:
     * the second bind succeeds silently instead of throwing.
     */
    @Test
    void bind_rejects_same_entity_bound_to_two_ships() {
        RuntimeBindingRegistry registry = new RuntimeBindingRegistry();
        ShipIdentity shipA = ShipIdentity.fromUuid(UUID.randomUUID());
        ShipIdentity shipB = ShipIdentity.fromUuid(UUID.randomUUID());
        UUID sharedEntity = UUID.randomUUID();

        registry.bind(RuntimeBinding.active(shipA, sharedEntity));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.bind(RuntimeBinding.active(shipB, sharedEntity)));
        assertTrue(ex.getMessage().contains("already bound"),
                "Exception must mention the existing binding");

        // Rollback verification: shipA's binding must remain intact after the failed second bind.
        Optional<RuntimeBinding> shipABinding = registry.findByShip(shipA);
        assertTrue(shipABinding.isPresent(), "Original binding must survive the failed bind");
        assertEquals(sharedEntity, shipABinding.get().entityUuid());
    }

    /**
     * FALSIFICATION_PROOF — DEFECT-02 (ship side).
     *
     * <p>Mutation plan: remove the ship-side {@code putIfAbsent} check. Re-run —
     * expected RED: the second bind overwrites the first instead of throwing.
     */
    @Test
    void bind_rejects_same_ship_bound_twice() {
        RuntimeBindingRegistry registry = new RuntimeBindingRegistry();
        ShipIdentity ship = ShipIdentity.fromUuid(UUID.randomUUID());
        UUID entity1 = UUID.randomUUID();
        UUID entity2 = UUID.randomUUID();

        registry.bind(RuntimeBinding.active(ship, entity1));

        assertThrows(IllegalStateException.class,
                () -> registry.bind(RuntimeBinding.active(ship, entity2)));
    }

    @Test
    void bind_rejects_non_active_binding() {
        RuntimeBindingRegistry registry = new RuntimeBindingRegistry();
        ShipIdentity ship = ShipIdentity.fromUuid(UUID.randomUUID());
        UUID entity = UUID.randomUUID();

        RuntimeBinding released = new RuntimeBinding(ship, entity, BindingState.RELEASED);

        assertThrows(IllegalArgumentException.class, () -> registry.bind(released));
    }

    @Test
    void findByShip_returns_active_binding_after_bind() {
        RuntimeBindingRegistry registry = new RuntimeBindingRegistry();
        ShipIdentity ship = ShipIdentity.fromUuid(UUID.randomUUID());
        UUID entity = UUID.randomUUID();
        registry.bind(RuntimeBinding.active(ship, entity));

        Optional<RuntimeBinding> found = registry.findByShip(ship);

        assertTrue(found.isPresent());
        assertEquals(entity, found.get().entityUuid());
        assertEquals(BindingState.ACTIVE, found.get().state());
    }

    @Test
    void findByEntity_returns_active_binding_after_bind() {
        RuntimeBindingRegistry registry = new RuntimeBindingRegistry();
        ShipIdentity ship = ShipIdentity.fromUuid(UUID.randomUUID());
        UUID entity = UUID.randomUUID();
        registry.bind(RuntimeBinding.active(ship, entity));

        Optional<RuntimeBinding> found = registry.findByEntity(entity);

        assertTrue(found.isPresent());
        assertEquals(ship, found.get().shipId());
    }

    /**
     * FALSIFICATION_PROOF — DEFECT-03 (vanilla entity misclassification).
     *
     * <p>This is the coexistence invariant (CON-02): a vanilla armor stand placed
     * by any other means must not resolve to a binding when queried by UUID.
     *
     * <p>Mutation plan: replace {@code findByEntity} body with
     * {@code byShip.values().stream().findFirst()} (returns any binding). Re-run —
     * expected RED: the assertion that an unknown UUID returns empty would fail
     * because the registry returns whatever binding happens to be first.
     */
    @Test
    void findByEntity_returns_empty_for_unknown_uuid() {
        RuntimeBindingRegistry registry = new RuntimeBindingRegistry();
        ShipIdentity ship = ShipIdentity.fromUuid(UUID.randomUUID());
        UUID boundEntity = UUID.randomUUID();
        UUID unknownEntity = UUID.randomUUID();
        registry.bind(RuntimeBinding.active(ship, boundEntity));

        Optional<RuntimeBinding> found = registry.findByEntity(unknownEntity);

        assertTrue(found.isEmpty(),
                "Unknown entity UUID must not resolve to a binding (DEFECT-03)");
    }

    @Test
    void findByShip_returns_empty_for_unknown_ship() {
        RuntimeBindingRegistry registry = new RuntimeBindingRegistry();

        Optional<RuntimeBinding> found = registry.findByShip(
                ShipIdentity.fromUuid(UUID.randomUUID()));

        assertTrue(found.isEmpty());
    }

    @Test
    void release_removes_binding_from_both_indices() {
        RuntimeBindingRegistry registry = new RuntimeBindingRegistry();
        ShipIdentity ship = ShipIdentity.fromUuid(UUID.randomUUID());
        UUID entity = UUID.randomUUID();
        registry.bind(RuntimeBinding.active(ship, entity));

        registry.release(ship);

        assertTrue(registry.findByShip(ship).isEmpty());
        assertTrue(registry.findByEntity(entity).isEmpty());
        assertEquals(0, registry.activeCount());
    }
}
