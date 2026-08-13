package com.glooshy.ships.listener;

import com.glooshy.ships.item.ShipCoreItem;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Listens for right-click water with a Ship Core, consumes the core, and
 * creates the ship in the registry.
 *
 * <p>V1: the ship exists only in the registry (identity + nothing else).
 * Future slices add a runtime entity at the click location and link it to
 * the ship identity (MOD-03 Runtime Binding Registry responsibility).
 */
public final class ShipCorePlacementListener implements Listener {

    private final ShipCoreItem shipCoreItem;
    private final ShipRegistry shipRegistry;

    public ShipCorePlacementListener(ShipCoreItem shipCoreItem, ShipRegistry shipRegistry) {
        this.shipCoreItem = shipCoreItem;
        this.shipRegistry = shipRegistry;
    }

    @EventHandler
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        if (clicked.getType() != Material.WATER) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (!shipCoreItem.isShipCore(inHand)) {
            return;
        }

        Ship ship = shipRegistry.createShip();

        inHand.setAmount(inHand.getAmount() - 1);

        Location loc = clicked.getLocation();
        player.playSound(loc, Sound.BLOCK_CONDUIT_ACTIVATE, 0.7f, 1.2f);
        player.sendMessage(net.kyori.adventure.text.Component.text(
                "Placed ship core (id " + ship.identity().encoded() + ")",
                net.kyori.adventure.text.format.NamedTextColor.GREEN));
    }
}
