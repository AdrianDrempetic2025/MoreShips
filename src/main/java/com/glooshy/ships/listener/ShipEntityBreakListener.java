package com.glooshy.ships.listener;

import com.glooshy.ships.item.ShipCoreItem;
import com.glooshy.ships.runtime.RuntimeBinding;
import com.glooshy.ships.runtime.RuntimeBindingRegistry;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
import com.glooshy.ships.ship.ShipTeardownService;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Listens for damage to the ArmorStand that represents a ship.
 *
 * <p>Two paths depending on ship phase:
 *
 * <p><b>Teardownable phases (UNFINISHED, HULL_APPLIED):</b> damage is cancelled
 * unconditionally — pre-finalization ships cannot be killed by external damage
 * (RQCA-21: inputs must be conserved via the explicit teardown flow, not lost
 * to creeper explosions / fall / etc.). Player damage triggers the full
 * teardown: drop core + hull, transition to REMOVED, release binding, remove
 * entity. Non-player damage is just cancelled.
 *
 * <p><b>FINALIZED phase:</b> damage is NOT cancelled. The ship has HP (a
 * proper HP system is a future slice; for now ArmorStand HP), so it eventually
 * dies from sustained damage. When the entity dies,
 * {@link ShipEntityDeathListener} catches {@code EntityDeathEvent} and
 * transitions the ship to DESTROYED + releases the binding.
 *
 * <p><b>DESTROYED phase:</b> the entity should already be gone; if damage
 * reaches a DESTROYED-phase entity somehow, it's let through (entity will be
 * removed naturally).
 */
public final class ShipEntityBreakListener implements Listener {

    private final ShipCoreItem shipCoreItem;
    private final RuntimeBindingRegistry bindingRegistry;
    private final ShipRegistry shipRegistry;
    private final ShipTeardownService teardownService;

    public ShipEntityBreakListener(
            ShipCoreItem shipCoreItem,
            RuntimeBindingRegistry bindingRegistry,
            ShipRegistry shipRegistry,
            ShipTeardownService teardownService) {
        this.shipCoreItem = shipCoreItem;
        this.bindingRegistry = bindingRegistry;
        this.shipRegistry = shipRegistry;
        this.teardownService = teardownService;
    }

    @EventHandler
    public void onEntityDamage(@NotNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof ArmorStand stand)) {
            return;
        }

        Optional<RuntimeBinding> binding = bindingRegistry.findByEntity(stand.getUniqueId());
        if (binding.isEmpty()) {
            return; // Not a custom ship — vanilla damage proceeds
        }

        var shipId = binding.get().shipId();
        Optional<Ship> shipOpt = shipRegistry.find(shipId);
        if (shipOpt.isEmpty()) {
            // Orphan binding — release it
            bindingRegistry.release(shipId);
            return;
        }
        Ship ship = shipOpt.get();

        if (!ShipTeardownService.isTeardownable(ship.phase())) {
            // FINALIZED or DESTROYED — let damage proceed. Ship has HP (future
            // proper combat slice; for now vanilla ArmorStand HP). When entity
            // dies, the death listener cleans up.
            return;
        }

        // Teardownable ship — cancel damage to keep the entity alive while we
        // decide whether to tear down. RQCA-21 requires inputs to be conserved
        // via the explicit teardown flow, not lost to ambient damage.
        event.setCancelled(true);

        Player player = damagerAsPlayer(event);
        if (player == null) {
            return; // Non-player damage: just protect, no teardown
        }

        // Drop inputs BEFORE mutating state — player keeps the inputs even if
        // the subsequent service call somehow fails.
        stand.getWorld().dropItemNaturally(stand.getLocation(), shipCoreItem.create());

        Material hull = ship.hullMaterial();
        if (hull != null) {
            stand.getWorld().dropItemNaturally(stand.getLocation(), new ItemStack(hull));
        }

        teardownService.teardown(shipId);
        stand.remove();

        String recovery = hull != null
                ? "Recovered Ship Core + " + hull.name() + " from ship."
                : "Recovered Ship Core from unfinished ship.";
        player.sendMessage(Component.text(recovery, NamedTextColor.GREEN));
    }

    private static Player damagerAsPlayer(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        return byEntity.getDamager() instanceof Player p ? p : null;
    }
}
