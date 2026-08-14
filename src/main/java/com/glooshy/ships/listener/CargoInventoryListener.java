package com.glooshy.ships.listener;

import com.glooshy.ships.cargo.CargoHolder;
import com.glooshy.ships.cargo.CargoService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Persists cargo inventory contents back to the ship registry when the cargo
 * GUI closes (RQCA-21/22: cargo is conserved, never silently dropped).
 */
public final class CargoInventoryListener implements Listener {

    private final CargoService cargoService;

    public CargoInventoryListener(CargoService cargoService) {
        this.cargoService = cargoService;
    }

    @EventHandler
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof CargoHolder holder)) {
            return;
        }
        boolean saved = cargoService.save(holder, event.getInventory());
        if (!saved) {
            event.getPlayer().sendMessage(Component.text(
                    "Ship no longer exists — cargo could not be saved. "
                            + "Take your items out of this inventory now or they are lost!",
                    NamedTextColor.RED));
        }
    }
}
