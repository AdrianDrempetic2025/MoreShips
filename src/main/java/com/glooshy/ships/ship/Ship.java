package com.glooshy.ships.ship;

import com.glooshy.ships.identity.ShipIdentity;
import java.util.Objects;

/**
 * Domain entity: a ship with an immutable {@link ShipIdentity} and a current
 * {@link LifecyclePhase}.
 *
 * <p>Identity is fixed at construction; phase changes via copy-of-this with a
 * new phase value. The {@link ShipRegistry} is the sole authority for phase
 * transitions; clients do not construct Ships directly.
 */
public record Ship(ShipIdentity identity, LifecyclePhase phase) {

    public Ship {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(phase, "phase");
    }
}
