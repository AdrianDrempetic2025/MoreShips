package com.glooshy.ships.ship;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.runtime.RuntimeBindingRegistry;
import java.util.Objects;

/**
 * Tears down an unfinished ship: transitions it to {@link LifecyclePhase#REMOVED}
 * and releases any active runtime binding.
 *
 * <p>Teardown is only valid for ships in pre-finalization phases (UNFINISHED,
 * HULL_APPLIED). Attempting teardown on a FINALIZED, DESTROYED, or REMOVED ship
 * throws — those phases are not teardownable. The listener is responsible for
 * dropping the Ship Core item at the entity location; the service handles only
 * the state side.
 */
public final class ShipTeardownService {

    private final ShipRegistry shipRegistry;
    private final RuntimeBindingRegistry bindingRegistry;

    public ShipTeardownService(ShipRegistry shipRegistry, RuntimeBindingRegistry bindingRegistry) {
        this.shipRegistry = Objects.requireNonNull(shipRegistry);
        this.bindingRegistry = Objects.requireNonNull(bindingRegistry);
    }

    /**
     * Is a ship in this phase teardownable? Pre-finalization phases only.
     */
    public static boolean isTeardownable(LifecyclePhase phase) {
        return phase == LifecyclePhase.UNFINISHED || phase == LifecyclePhase.HULL_APPLIED;
    }

    /**
     * Tear down the named ship. Requires the ship to be in a teardownable phase.
     *
     * @throws IllegalStateException if the ship is not found, or not in a
     *                              teardownable phase
     */
    public void teardown(ShipIdentity shipId) {
        Objects.requireNonNull(shipId);

        Ship ship = shipRegistry.find(shipId)
                .orElseThrow(() -> new IllegalStateException("Ship not found: " + shipId));

        if (!isTeardownable(ship.phase())) {
            throw new IllegalStateException(
                    "Cannot teardown ship in phase " + ship.phase()
                            + " (only UNFINISHED and HULL_APPLIED are teardownable)");
        }

        shipRegistry.transition(shipId, LifecyclePhase.REMOVED);
        bindingRegistry.release(shipId);
    }
}
