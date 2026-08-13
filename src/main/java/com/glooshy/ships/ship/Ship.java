package com.glooshy.ships.ship;

import com.glooshy.ships.identity.ShipIdentity;
import java.util.Objects;

/**
 * Domain entity: a ship, identified by a unique {@link ShipIdentity}.
 *
 * <p>V1 carries identity only. Hull material, modules, lifecycle phase,
 * integrity, and occupancy come in later slices.
 */
public record Ship(ShipIdentity identity) {

    public Ship {
        Objects.requireNonNull(identity, "identity must not be null");
    }
}
