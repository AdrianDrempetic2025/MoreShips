package com.glooshy.ships.listener;

import com.glooshy.ships.combat.CannonService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Persists cannon inventory contents back to the ship registry when the
 * cannon GUI closes (Session 2: ammo/fuel/shots survive). Items that are
 * neither ammo nor fuel are handed back to the player instead of stored.
 */
public final class CannonInventoryListener implements Listener {

    private final CannonService cannons;

    public CannonInventoryListener(CannonService cannons) {
        this.cannons = cannons;
    }

    @EventHandler
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof CannonService.Holder holder)) {
            return;
        }
        if (!(event.getPlayer() instanceof org.bukkit.entity.Player player)) {
            return;
        }
        boolean saved = cannons.save(holder, event.getInventory(), player);
        if (!saved) {
            player.sendMessage(Component.text(
                    "Ship no longer exists — cannon contents could not be saved. "
                            + "Take your items out of this inventory now or they are lost!",
                    NamedTextColor.RED));
        }
    }

    /** Filler slots (9-17) between the cannon and player regions are not a place. */
    @EventHandler
    public void onInventoryClick(@NotNull org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CannonService.Holder)) {
            return;
        }
        if (CannonService.isFillerSlot(event.getRawSlot())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(@NotNull org.bukkit.event.inventory.InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof CannonService.Holder)) {
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (CannonService.isFillerSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
