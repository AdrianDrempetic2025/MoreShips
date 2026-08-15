package com.glooshy.ships.listener;

import com.glooshy.ships.item.ModuleItem;
import com.glooshy.ships.item.ShipCoreItem;
import com.glooshy.ships.ui.RecipeBookUi;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;

/** The recipe book UI is read-only: clicks only navigate pages. */
public final class RecipeBookListener implements Listener {

    private final ShipCoreItem cores;
    private final ModuleItem modules;

    private final com.glooshy.ships.item.CannonballItem cannonballs;

    public RecipeBookListener(ShipCoreItem cores, ModuleItem modules,
                              com.glooshy.ships.item.CannonballItem cannonballs) {
        this.cores = cores;
        this.modules = modules;
        this.cannonballs = cannonballs;
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RecipeBookUi.Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        RecipeBookUi ui = new RecipeBookUi(cores, modules, cannonballs);
        if (event.getSlot() == RecipeBookUi.PREV_SLOT) {
            ui.open(player, holder.page - 1);
        } else if (event.getSlot() == RecipeBookUi.NEXT_SLOT) {
            ui.open(player, holder.page + 1);
        }
    }
}
