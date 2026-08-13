package com.glooshy.ships.ship;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.identity.ShipIdentityGenerator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Authoritative owner of the live ship set.
 *
 * <p>Every ship created through this registry receives a fresh identity from
 * the injected {@link ShipIdentityGenerator}. The registry rejects collisions
 * defensively, even though a UUID generator should never produce one — this is
 * the safety net for DEFECT-01 (duplicate identity assignment).
 *
 * <p>Thread-safe: creation may be called from any thread.
 */
public final class ShipRegistry {

    private final ShipIdentityGenerator generator;
    private final ConcurrentMap<ShipIdentity, Ship> ships = new ConcurrentHashMap<>();

    public ShipRegistry(ShipIdentityGenerator generator) {
        this.generator = Objects.requireNonNull(generator, "generator");
    }

    public Ship createShip() {
        ShipIdentity identity = generator.generate();
        Ship ship = new Ship(identity);
        Ship existing = ships.putIfAbsent(identity, ship);
        if (existing != null) {
            throw new IllegalStateException(
                "Ship identity collision: " + identity + " already in registry");
        }
        return ship;
    }

    public Optional<Ship> find(ShipIdentity identity) {
        return Optional.ofNullable(ships.get(identity));
    }

    public int size() {
        return ships.size();
    }
}
