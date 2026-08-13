package com.glooshy.ships.listener;

import com.glooshy.ships.runtime.RuntimeBinding;
import com.glooshy.ships.runtime.RuntimeBindingRegistry;
import com.glooshy.ships.ship.LifecyclePhase;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
import java.util.Optional;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Listens for the death of a ship's ArmorStand entity. When a FINALIZED ship's
 * entity dies (HP reaches 0 via sustained damage), the ship transitions to
 * DESTROYED — which deletes it from the registry and releases the binding.
 *
 * <p>This is the cleanup path that was missing in BUILD-05b's first attempt.
 * Without it, the entity disappeared but the ship stayed in the registry
 * (orphan counter bug).
 *
 * <p>Pre-finalization ships do NOT reach this listener because
 * {@link ShipEntityBreakListener} cancels their damage. So this listener only
 * fires meaningfully for FINALIZED ships.
 */
public final class ShipEntityDeathListener implements Listener {

    private final ShipRegistry shipRegistry;
    private final RuntimeBindingRegistry bindingRegistry;

    public ShipEntityDeathListener(
            ShipRegistry shipRegistry,
            RuntimeBindingRegistry bindingRegistry) {
        this.shipRegistry = shipRegistry;
        this.bindingRegistry = bindingRegistry;
    }

    @EventHandler
    public void onEntityDeath(@NotNull EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof ArmorStand)) {
            return;
        }

        Optional<RuntimeBinding> binding = bindingRegistry.findByEntity(entity.getUniqueId());
        if (binding.isEmpty()) {
            return; // Not a custom ship
        }

        var shipId = binding.get().shipId();
        Optional<Ship> shipOpt = shipRegistry.find(shipId);
        if (shipOpt.isEmpty()) {
            // Ship already gone (race or prior cleanup) — release lingering binding
            bindingRegistry.release(shipId);
            return;
        }
        Ship ship = shipOpt.get();

        if (ship.phase() != LifecyclePhase.FINALIZED) {
            // Pre-finalization ships shouldn't reach here (damage was cancelled).
            // If they do somehow, leave them alone — defense in depth.
            return;
        }

        try {
            shipRegistry.transition(shipId, LifecyclePhase.DESTROYED);
        } catch (IllegalStateException ignored) {
            // Race or already destroyed — best-effort cleanup
        }
        bindingRegistry.release(shipId);
    }
}
