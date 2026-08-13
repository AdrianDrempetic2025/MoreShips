package com.glooshy.ships.ship;

import static org.junit.jupiter.api.Assertions.*;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.identity.ShipIdentityGenerator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * FALSIFICATION_PROOF + REGRESSION_GUARD for ship identity uniqueness (RQCA-26).
 */
class ShipRegistryTest {

    /**
     * FALSIFICATION_PROOF — DEFECT-01: DUPLICATE_ID_ASSIGNED.
     *
     * <p>Named defect: the registry assigns the same identity to two distinct ships.
     * This happens if the generator returns a constant ID and the registry does not
     * detect the collision.
     *
     * <p>Mutation plan: replace {@code ShipIdentityGenerator.uuid()} with
     * {@code constantGenerator()} (defined below) and re-run. Expected RED either as
     * an {@link IllegalStateException} from the defensive collision check, or as an
     * assertion failure if the check is also mutated away.
     */
    @Test
    void createShip_produces_distinct_identities() {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid(), new com.glooshy.ships.hull.HpCalculator(10.0));

        Ship first = registry.createShip();
        Ship second = registry.createShip();

        assertNotEquals(first.identity(), second.identity(),
                "Two ships must have distinct identities (DEFECT-01)");
        assertEquals(2, registry.size(),
                "Registry must contain both ships");
    }

    /**
     * REGRESSION_GUARD — many creations never collide under the UUID generator.
     *
     * <p>This is a regression guard, not a falsification proof — it asserts the
     * default behavior but does not target a named defect with a mutation plan.
     * Statistical drift in UUID generation would surface here.
     */
    @Test
    void createShip_many_creations_remain_distinct() {
        ShipRegistry registry = new ShipRegistry(ShipIdentityGenerator.uuid(), new com.glooshy.ships.hull.HpCalculator(10.0));
        Set<ShipIdentity> seen = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            Ship ship = registry.createShip();
            assertTrue(seen.add(ship.identity()),
                    "Identity " + ship.identity() + " appeared more than once");
        }
    }

    /**
     * Mutation witness — the defensive collision check fires when the generator
     * is broken. This is what produces RED under the DEFECT-01 mutation.
     */
    @Test
    void createShip_rejects_duplicate_identity_from_generator() {
        ShipIdentityGenerator constant = constantGenerator();
        ShipRegistry registry = new ShipRegistry(constant, new com.glooshy.ships.hull.HpCalculator(10.0));

        registry.createShip();

        IllegalStateException ex = assertThrows(IllegalStateException.class, registry::createShip);
        assertTrue(ex.getMessage().contains("collision"),
                "Collision message must mention collision");
    }

    /**
     * Helper — the DEFECT-01 mutation. Replace {@code ShipIdentityGenerator.uuid()}
     * with this in any test to witness the proof fire.
     */
    private static ShipIdentityGenerator constantGenerator() {
        ShipIdentity fixed = ShipIdentity.fromUuid(
                UUID.fromString("00000000-0000-0000-0000-000000000000"));
        return () -> fixed;
    }
}
