package com.glooshy.ships.ship;

import com.glooshy.ships.identity.ShipIdentity;
import java.util.Objects;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

/**
 * Domain entity: a ship with an immutable {@link ShipIdentity}, a current
 * {@link LifecyclePhase}, and (once hull is applied) a {@link Material} hull.
 *
 * <p>Identity is fixed at construction; phase changes via copy-of-this with a
 * new phase value (preserving hullMaterial). The {@link ShipRegistry} is the
 * sole authority for transitions and hull application.
 */
public record Ship(ShipIdentity identity, LifecyclePhase phase, @Nullable Material hullMaterial) {

    public Ship {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(phase, "phase");
    }
}
