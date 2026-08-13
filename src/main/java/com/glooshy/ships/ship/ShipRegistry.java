package com.glooshy.ships.ship;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.identity.ShipIdentityGenerator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Authoritative owner of the live ship set + ship lifecycle state + hull
 * material.
 *
 * <p>Every ship starts in {@link LifecyclePhase#UNFINISHED} with null hull
 * material. Hull application transitions UNFINISHED → HULL_APPLIED and sets
 * the hull material atomically. Further phase transitions preserve the hull.
 *
 * <p>Transition to {@link LifecyclePhase#REMOVED} deletes the entry entirely
 * (terminal "gone" state). DESTROYED stays in the map for future wreck lookup.
 */
public final class ShipRegistry {

    private final ShipIdentityGenerator generator;
    private final ConcurrentMap<ShipIdentity, Ship> ships = new ConcurrentHashMap<>();

    public ShipRegistry(ShipIdentityGenerator generator) {
        this.generator = Objects.requireNonNull(generator, "generator");
    }

    public Ship createShip() {
        ShipIdentity identity = generator.generate();
        Ship ship = new Ship(identity, LifecyclePhase.UNFINISHED, null);

        Ship existing = ships.putIfAbsent(identity, ship);
        if (existing != null) {
            throw new IllegalStateException(
                    "Ship identity collision: " + identity + " already in registry");
        }
        return ship;
    }

    public Ship applyHull(ShipIdentity identity, @Nullable Material hullMaterial) {
        Objects.requireNonNull(identity, "identity");
        // hullMaterial is @Nullable for testability — org.bukkit.Material's
        // static initializer requires server context, so unit tests cannot
        // construct or reference Material constants. Production callers (the
        // hull-application listener) MUST pass a validated non-null Material;
        // the validator + listener guard that contract.
        return ships.compute(identity, (key, current) -> {
            if (current == null) {
                throw new IllegalStateException("Ship not found: " + identity);
            }
            if (current.phase() != LifecyclePhase.UNFINISHED) {
                throw new IllegalStateException(
                        "Cannot apply hull to ship in phase " + current.phase()
                                + " (only UNFINISHED ships accept hull)");
            }
            return new Ship(key, LifecyclePhase.HULL_APPLIED, hullMaterial);
        });
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
            if (newPhase == LifecyclePhase.REMOVED) {
                return null;
            }
            return new Ship(key, newPhase, current.hullMaterial());
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

    /**
     * Replace the entire live ship set with the given ships (persistence load).
     * Existing state is wiped.
     */
    public void load(@NotNull java.util.List<Ship> toLoad) {
        Objects.requireNonNull(toLoad);
        ships.clear();
        for (Ship ship : toLoad) {
            ships.put(ship.identity(), ship);
        }
    }

    /**
     * Snapshot the current live ships for persistence save.
     */
    public @NotNull java.util.List<Ship> snapshot() {
        return List.copyOf(ships.values());
    }
}
