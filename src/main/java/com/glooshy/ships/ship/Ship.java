package com.glooshy.ships.ship;

import com.glooshy.ships.identity.ShipIdentity;
import java.util.Objects;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

/**
 * Domain entity: a ship with an immutable {@link ShipIdentity}, a current
 * {@link LifecyclePhase}, hull material, and current/max HP.
 *
 * <p>HP fields are -1 for ships that have not yet had a hull applied
 * (UNFINISHED). On hull application, currentHp and maxHp are set to the
 * computed value derived from material hardness. Damage reduces currentHp;
 * at 0, the ship transitions to DESTROYED.
 */
public record Ship(
        ShipIdentity identity,
        LifecyclePhase phase,
        @Nullable Material hullMaterial,
        int currentHp,
        int maxHp) {

    public Ship {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(phase, "phase");
    }

    /**
     * Convenience constructor for ships without hull material (e.g., new
     * ships). HP fields default to -1 (sentinel for "no HP yet").
     */
    public Ship(ShipIdentity identity, LifecyclePhase phase, @Nullable Material hullMaterial) {
        this(identity, phase, hullMaterial, -1, -1);
    }
}
