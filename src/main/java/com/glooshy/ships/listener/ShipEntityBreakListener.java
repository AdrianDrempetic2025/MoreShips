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
 * <p>For ANY custom-ship entity, the damage event is cancelled — ships have
 * their own damage model (HP from the future combat slice), not vanilla
 * ArmorStand damage. Cancelling prevents the ArmorStand from being destroyed
 * by melee, explosions, fall, etc. — which would orphan the ship in the
 * registry (DEFECT-11: FINALIZED_SHIP_BREAKABLE_VIA_MELEE).
 *
 * <p>If the damager is a player and the ship is in a teardownable phase
 * (UNFINISHED or HULL_APPLIED), the listener performs full teardown:
 * drops the Ship Core (and hull material if applied), transitions the ship
 * to REMOVED, releases the binding, and removes the entity.
 *
 * <p>For FINALIZED / DESTROYED ships, the damage is cancelled but no teardown
 * occurs — those phases have their own removal paths (HP=0 → DESTROYED;
 * future slice).
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

        // Custom ship entity — ALWAYS cancel damage. The ship has its own
        // damage model (HP from future combat slice). Vanilla ArmorStand
        // damage is not the destruction path.
        event.setCancelled(true);

        var shipId = binding.get().shipId();
        Optional<Ship> shipOpt = shipRegistry.find(shipId);
        if (shipOpt.isEmpty()) {
            // Orphan binding (ship was REMOVED but binding lingers) — release
            bindingRegistry.release(shipId);
            return;
        }
        Ship ship = shipOpt.get();

        // Only player damage triggers teardown consideration
        Player player = damagerAsPlayer(event);
        if (player == null) {
            return; // Non-player damage: just protect, no teardown
        }

        if (!ShipTeardownService.isTeardownable(ship.phase())) {
            // FINALIZED / DESTROYED — invincible to melee in V1
            player.sendMessage(Component.text(
                    "This ship cannot be damaged by melee.",
                    NamedTextColor.GRAY));
            return;
        }

        // Pre-finalization teardown — drop inputs BEFORE state mutation
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
