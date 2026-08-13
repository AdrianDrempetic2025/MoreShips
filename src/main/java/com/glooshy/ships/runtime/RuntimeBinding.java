package com.glooshy.ships.runtime;

import com.glooshy.ships.identity.ShipIdentity;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable binding between a {@link ShipIdentity} and a runtime platform entity.
 *
 * <p>Value object — equality by all three fields (shipId, entityUuid, state).
 */
public record RuntimeBinding(
        ShipIdentity shipId,
        UUID entityUuid,
        BindingState state) {

    public RuntimeBinding {
        Objects.requireNonNull(shipId, "shipId");
        Objects.requireNonNull(entityUuid, "entityUuid");
        Objects.requireNonNull(state, "state");
    }

    public static RuntimeBinding active(ShipIdentity shipId, UUID entityUuid) {
        return new RuntimeBinding(shipId, entityUuid, BindingState.ACTIVE);
    }

    public RuntimeBinding withState(BindingState newState) {
        return new RuntimeBinding(shipId, entityUuid, newState);
    }
}
