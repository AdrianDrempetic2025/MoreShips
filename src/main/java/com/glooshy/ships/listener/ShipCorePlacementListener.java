package com.glooshy.ships.listener;

import com.glooshy.ships.item.ShipCoreItem;
import com.glooshy.ships.runtime.RuntimeBinding;
import com.glooshy.ships.runtime.RuntimeBindingRegistry;
import com.glooshy.ships.runtime.ShipEntitySpawner;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
 * Listens for right-click water with a Ship Core. On placement:
 * <ol>
 *   <li>create ship in {@link ShipRegistry} (gets unique identity)</li>
 *   <li>spawn unfinished-ship entity at the click location</li>
 *   <li>bind ship↔entity in {@link RuntimeBindingRegistry}</li>
 *   <li>consume one Ship Core from the player's hand</li>
 * </ol>
 *
 * <p>Failure atomicity gap: if step 3 throws (e.g., duplicate entity UUID), the
 * ship is in the registry and the entity exists in the world, but no binding
 * connects them. Under normal operation this cannot happen — Bukkit entity
 * UUIDs are unique by construction. The gap is recorded; a future slice adds
 * transactional spawn-or-release.
 */
public final class ShipCorePlacementListener implements Listener {

    private final ShipCoreItem shipCoreItem;
    private final ShipRegistry shipRegistry;
    private final ShipEntitySpawner entitySpawner;
    private final RuntimeBindingRegistry bindingRegistry;

    public ShipCorePlacementListener(
            ShipCoreItem shipCoreItem,
            ShipRegistry shipRegistry,
            ShipEntitySpawner entitySpawner,
            RuntimeBindingRegistry bindingRegistry) {
        this.shipCoreItem = shipCoreItem;
        this.shipRegistry = shipRegistry;
        this.entitySpawner = entitySpawner;
        this.bindingRegistry = bindingRegistry;
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

        Location spawnLoc = clicked.getLocation().add(0.5, 1.0, 0.5);
        UUID entityUuid = entitySpawner.spawnUnfinishedShip(spawnLoc, ship.identity());

        bindingRegistry.bind(RuntimeBinding.active(ship.identity(), entityUuid));

        inHand.setAmount(inHand.getAmount() - 1);

        player.playSound(spawnLoc, Sound.BLOCK_CONDUIT_ACTIVATE, 0.7f, 1.2f);
        player.sendMessage(Component.text(
                "Placed ship core (id " + ship.identity().encoded() + ")",
                NamedTextColor.GREEN));
    }
}
