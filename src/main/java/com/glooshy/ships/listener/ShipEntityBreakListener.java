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
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Listens for a player damaging the ArmorStand that represents an unfinished
 * ship. On damage, drops a Ship Core item at the entity location, transitions
 * the ship to REMOVED, releases the binding, and removes the entity from the
 * world. The damage event itself is cancelled so the ArmorStand does not take
 * real damage in the interim (DEFECT-04 / RQCA-21).
 *
 * <p>V1 uses {@link EntityDamageByEntityEvent} rather than a paper-specific
 * ArmorStandBreakEvent because the latter is not present in Paper 26.2's API
 * surface. Damage event catches the same player-attacks-stand interaction.
 *
 * <p>Order of operations is intentional: the core is dropped BEFORE the
 * teardown service runs, so the player gets their input back even if the
 * service throws. The service is then expected to succeed because we just
 * verified the phase via {@link ShipTeardownService#isTeardownable}; a throw
 * at that point is a logic bug, not a state issue.
 *
 * <p>Finalized ships are not teardownable via this path — damaging the
 * ArmorStand of a finalized ship is a no-op for this listener.
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
    public void onEntityDamageByEntity(@NotNull EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ArmorStand stand)) {
            return;
        }
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        Optional<RuntimeBinding> binding = bindingRegistry.findByEntity(stand.getUniqueId());
        if (binding.isEmpty()) {
            return; // Not a custom ship entity; vanilla ArmorStand damage proceeds
        }

        var shipId = binding.get().shipId();
        Optional<Ship> shipOpt = shipRegistry.find(shipId);
        if (shipOpt.isEmpty()) {
            // Orphan binding (shouldn't happen, but defensive) — release it
            bindingRegistry.release(shipId);
            return;
        }
        Ship ship = shipOpt.get();

        if (!ShipTeardownService.isTeardownable(ship.phase())) {
            return; // Don't touch FINALIZED/DESTROYED ships via this path
        }

        // Cancel the damage so the ArmorStand doesn't take real damage while
        // we clean up. We're going to remove the entity manually.
        event.setCancelled(true);

        // Drop the Ship Core BEFORE mutating state — player keeps the input
        // even if the subsequent service call somehow fails.
        stand.getWorld().dropItemNaturally(stand.getLocation(), shipCoreItem.create());

        // If the ship had a hull applied, return that block too (RQCA-21:
        // teardown returns ALL inputs, not just the core).
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
}
