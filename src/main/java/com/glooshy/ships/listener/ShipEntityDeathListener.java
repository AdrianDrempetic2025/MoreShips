package com.glooshy.ships.listener;

import com.glooshy.ships.runtime.RuntimeBinding;
import com.glooshy.ships.runtime.RuntimeBindingRegistry;
import com.glooshy.ships.ship.LifecyclePhase;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Listens for the death of a ship's ArmorStand entity. When a FINALIZED ship's
 * entity dies (HP reaches 0 via sustained damage), the ship transitions to
 * DESTROYED — which deletes it from the registry and releases the binding.
 *
 * <p>Per spec (raw §52, §55), destroyed ships become a wreck entity (future
 * slice). For V1 without wrecks:
 * <ul>
 *   <li>hull material is NOT dropped — it's part of the ship's matter, not
 *       recoverable loot. Modules and cargo will eventually route through
 *       the wreck (probabilistic for modules, 100% for cargo per RQCA-22).</li>
 *   <li>the entity just disappears; ship state transitions to DESTROYED.</li>
 * </ul>
 *
 * <p>Pre-finalization ships do NOT reach this listener because
 * {@link ShipEntityBreakListener} cancels their damage.
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
            bindingRegistry.release(shipId);
            return;
        }
        Ship ship = shipOpt.get();

        if (ship.phase() != LifecyclePhase.FINALIZED) {
            // Pre-finalization ships shouldn't reach here (damage was cancelled).
            return;
        }

        // Hull material is part of the ship — not dropped on destruction.
        // Future wreck slice routes modules (probabilistic) + cargo (100% via
        // physical inventories at the wreck).
        event.getDrops().clear();

        try {
            shipRegistry.transition(shipId, LifecyclePhase.DESTROYED);
        } catch (IllegalStateException ignored) {
            // Race or already destroyed — best-effort cleanup
        }
        bindingRegistry.release(shipId);

        Player killer = ((ArmorStand) entity).getKiller();
        if (killer != null) {
            killer.sendMessage(Component.text(
                    "Ship " + ship.identity().encoded() + " destroyed.",
                    NamedTextColor.RED));
        }
    }
}
