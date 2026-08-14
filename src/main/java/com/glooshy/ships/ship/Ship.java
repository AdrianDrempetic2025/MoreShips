package com.glooshy.ships.ship;

import com.glooshy.ships.identity.ShipIdentity;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

/**
 * Domain entity: a ship with an immutable {@link ShipIdentity}, a current
 * {@link LifecyclePhase}, hull material, current/max HP, and fitted modules
 * (slot → type, RQCA-08).
 *
 * <p>HP fields are -1 for ships that have not yet had a hull applied
 * (UNFINISHED). On hull application, currentHp and maxHp are set to the
 * computed value derived from material hardness. Damage reduces currentHp;
 * at 0, the ship transitions to DESTROYED.
 *
 * <p>Modules can only be fitted while the ship is HULL_APPLIED (before
 * finalization); the registry enforces this. The map is unmodifiable.
 */
public record Ship(
        ShipIdentity identity,
        LifecyclePhase phase,
        @Nullable Material hullMaterial,
        int currentHp,
        int maxHp,
        Map<ModuleSlot, ModuleType> modules) {

    public Ship {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(phase, "phase");
        modules = modules == null ? Map.of() : Map.copyOf(modules);
    }

    /**
     * Convenience constructor for ships without hull material (e.g., new
     * ships). HP fields default to -1 (sentinel for "no HP yet"), no modules.
     */
    public Ship(ShipIdentity identity, LifecyclePhase phase, @Nullable Material hullMaterial) {
        this(identity, phase, hullMaterial, -1, -1, Map.of());
    }

    /**
     * Convenience constructor without modules (keeps existing call sites
     * working).
     */
    public Ship(ShipIdentity identity, LifecyclePhase phase, @Nullable Material hullMaterial,
                int currentHp, int maxHp) {
        this(identity, phase, hullMaterial, currentHp, maxHp, Map.of());
    }
}
