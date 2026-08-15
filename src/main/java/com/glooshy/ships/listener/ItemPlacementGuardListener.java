package com.glooshy.ships.listener;

import com.glooshy.ships.item.ShipCoreItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Marked items (ship cores, modules) must never place as vanilla blocks —
 * a Dragon Egg core would place a real egg, a Cargo module a real chest.
 * Cores spawn ships via their own placement listener; modules only ever fit
 * onto ships.
 */
public final class ItemPlacementGuardListener implements Listener {

    private final com.glooshy.ships.item.ModuleItem moduleItem;
    private final ShipCoreItem coreItem;

    public ItemPlacementGuardListener(com.glooshy.ships.item.ModuleItem moduleItem,
                                      ShipCoreItem coreItem) {
        this.moduleItem = moduleItem;
        this.coreItem = coreItem;
    }

    @EventHandler
    public void onBlockPlace(@NotNull BlockPlaceEvent event) {
        var inHand = event.getItemInHand();
        if (moduleItem.parse(inHand).isPresent() || coreItem.parseSize(inHand) != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text(
                    "This item is part of a ship — it cannot be placed as a block.",
                    NamedTextColor.RED));
        }
    }
}
