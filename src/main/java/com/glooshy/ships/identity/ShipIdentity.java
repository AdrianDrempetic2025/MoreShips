package com.glooshy.ships.identity;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, unique identifier for a ship.
 *
 * <p>Value object — equality by UUID value. Persists across server restarts
 * when stored via its string encoding.
 */
public record ShipIdentity(UUID value) {

    public ShipIdentity {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static ShipIdentity fromUuid(UUID uuid) {
        return new ShipIdentity(uuid);
    }

    public String encoded() {
        return value.toString();
    }

    public static ShipIdentity decode(String encoded) {
        return new ShipIdentity(UUID.fromString(encoded));
    }
}
