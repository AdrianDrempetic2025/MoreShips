package com.glooshy.ships.ship;

import com.glooshy.ships.hull.HpCalculator;
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
 * material + HP.
 *
 * <p>Every ship starts in {@link LifecyclePhase#UNFINISHED} with null hull
 * material and HP=-1. Hull application transitions UNFINISHED → HULL_APPLIED,
 * stores the hull material, and computes max HP via {@link HpCalculator}.
 * Damage reduces current HP; at 0, callers transition to DESTROYED.
 *
 * <p>Transition to {@link LifecyclePhase#REMOVED} or {@link
 * LifecyclePhase#DESTROYED} deletes the entry entirely.
 */
public final class ShipRegistry {

    private final ShipIdentityGenerator generator;
    private final HpCalculator hpCalculator;
    private final ConcurrentMap<ShipIdentity, Ship> ships = new ConcurrentHashMap<>();

    public ShipRegistry(ShipIdentityGenerator generator, HpCalculator hpCalculator) {
        this.generator = Objects.requireNonNull(generator, "generator");
        this.hpCalculator = Objects.requireNonNull(hpCalculator, "hpCalculator");
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
        return ships.compute(identity, (key, current) -> {
            if (current == null) {
                throw new IllegalStateException("Ship not found: " + identity);
            }
            if (current.phase() != LifecyclePhase.UNFINISHED) {
                throw new IllegalStateException(
                        "Cannot apply hull to ship in phase " + current.phase()
                                + " (only UNFINISHED ships accept hull)");
            }
            int maxHp = hullMaterial != null ? hpCalculator.computeMaxHp(hullMaterial.getHardness()) : -1;
            return new Ship(key, LifecyclePhase.HULL_APPLIED, hullMaterial, maxHp, maxHp);
        });
    }

    /**
     * Apply damage to a FINALIZED ship's HP. Returns the updated ship.
     *
     * @throws IllegalStateException if the ship is not found or not FINALIZED
     */
    public Ship applyDamage(ShipIdentity identity, double amount) {
        Objects.requireNonNull(identity, "identity");
        if (amount < 0) {
            throw new IllegalArgumentException("Damage amount must be non-negative, got " + amount);
        }
        return ships.compute(identity, (key, current) -> {
            if (current == null) {
                throw new IllegalStateException("Ship not found: " + identity);
            }
            if (current.phase() != LifecyclePhase.FINALIZED) {
                throw new IllegalStateException(
                        "Cannot apply damage to ship in phase " + current.phase()
                                + " (only FINALIZED ships take damage)");
            }
            int newHp = Math.max(0, current.currentHp() - (int) Math.round(amount));
            return new Ship(key, current.phase(), current.hullMaterial(), newHp, current.maxHp());
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
            if (newPhase == LifecyclePhase.REMOVED || newPhase == LifecyclePhase.DESTROYED) {
                return null;
            }
            return new Ship(key, newPhase, current.hullMaterial(),
                    current.currentHp(), current.maxHp());
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

    public void load(@NotNull List<Ship> toLoad) {
        Objects.requireNonNull(toLoad);
        ships.clear();
        for (Ship ship : toLoad) {
            ships.put(ship.identity(), ship);
        }
    }

    public @NotNull List<Ship> snapshot() {
        return List.copyOf(ships.values());
    }
}
