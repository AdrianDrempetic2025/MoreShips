package com.glooshy.ships.ship;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.identity.ShipIdentityGenerator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Authoritative owner of the live ship set + ship lifecycle state.
 *
 * <p>Every ship starts in {@link LifecyclePhase#UNFINISHED} when created.
 * Transitions are validated by {@link LifecycleTransition}; invalid transitions
 * throw {@link IllegalStateException} (defense for DEFECT-05 ILLEGAL_TRANSITION).
 *
 * <p>Thread-safe: createShip and transition may be called from any thread.
 * transition uses {@link ConcurrentMap#compute} to ensure atomicity.
 */
public final class ShipRegistry {

    private final ShipIdentityGenerator generator;
    private final ConcurrentMap<ShipIdentity, Ship> ships = new ConcurrentHashMap<>();

    public ShipRegistry(ShipIdentityGenerator generator) {
        this.generator = Objects.requireNonNull(generator, "generator");
    }

    public Ship createShip() {
        ShipIdentity identity = generator.generate();
        Ship ship = new Ship(identity, LifecyclePhase.UNFINISHED);

        Ship existing = ships.putIfAbsent(identity, ship);
        if (existing != null) {
            throw new IllegalStateException(
                    "Ship identity collision: " + identity + " already in registry");
        }
        return ship;
    }

    public Ship transition(ShipIdentity identity, LifecyclePhase newPhase) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(newPhase, "newPhase");
        return ships.compute(identity, (key, current) -> {
            if (current == null) {
                throw new IllegalStateException("Ship not found: " + identity);
            }
            if (!LifecycleTransition.isValid(current.phase(), newPhase)) {
                throw new IllegalStateException(
                        "Invalid lifecycle transition: " + current.phase() + " → " + newPhase
                                + " (allowed: " + LifecycleTransition.validTargets(current.phase()) + ")");
            }
            return new Ship(key, newPhase);
        });
    }

    public Optional<Ship> find(ShipIdentity identity) {
        return Optional.ofNullable(ships.get(identity));
    }

    public Optional<LifecyclePhase> phaseOf(ShipIdentity identity) {
        return Optional.ofNullable(ships.get(identity)).map(Ship::phase);
    }

    public int size() {
        return ships.size();
    }

    public void remove(ShipIdentity identity) {
        ships.remove(identity);
    }
}
