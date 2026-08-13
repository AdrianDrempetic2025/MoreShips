package com.glooshy.ships.identity;

import java.util.UUID;

/**
 * Strategy for generating fresh ship identities.
 *
 * <p>Interface so tests can inject a constant ID generator to falsify
 * the uniqueness invariant (DEFECT-01 mutation).
 */
@FunctionalInterface
public interface ShipIdentityGenerator {

    ShipIdentity generate();

    static ShipIdentityGenerator uuid() {
        return () -> ShipIdentity.fromUuid(UUID.randomUUID());
    }
}
